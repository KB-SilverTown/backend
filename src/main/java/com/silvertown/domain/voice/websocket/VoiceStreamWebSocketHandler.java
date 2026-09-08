package com.silvertown.domain.voice.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.silvertown.domain.voice.dto.VoiceSessionDetailResponse;
import com.silvertown.domain.voice.dto.VoiceTurnResponse;
import com.silvertown.domain.voice.enums.SttMode;
import com.silvertown.domain.voice.enums.VoiceFlowType;
import com.silvertown.domain.voice.enums.VoiceSessionStatus;
import com.silvertown.domain.voice.service.VoiceSessionService;
import com.silvertown.domain.voice.service.VoiceTurnService;
import com.silvertown.domain.voice.stt.AzureSpeechDetailedResult;
import com.silvertown.domain.voice.stt.AzureSpeechRecognitionListener;
import com.silvertown.domain.voice.stt.AzureSpeechRecognitionStream;
import com.silvertown.domain.voice.stt.AzureSpeechV2StreamingClient;
import com.silvertown.domain.voice.validation.VoiceIdentifierPattern;
import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

/**
 * Transfer-only audio ingress. AUDIO is sent as binary WebSocket frames after a START event;
 * START, STOP and BARGE_IN remain documented JSON control events.
 */
@Slf4j
@Component
public class VoiceStreamWebSocketHandler extends AbstractWebSocketHandler {
    private static final Pattern SESSION_PATH = Pattern.compile(
            "/api/voice/sessions/(" + VoiceIdentifierPattern.CANONICAL_UUID_PATTERN + ")/stream$");
    private static final String START = "START";
    private static final String RESUME = "RESUME";
    private static final String STOP = "STOP";
    private static final String BARGE_IN = "BARGE_IN";
    private static final int AUDIO_SEQUENCE_HEADER_BYTES = Integer.BYTES;
    private static final int MAX_PCM_FRAME_BYTES = 64 * 1024;
    private static final long MAX_UNSIGNED_INT = 0xFFFF_FFFFL;
    private static final long DEFAULT_RESUME_GRACE_MILLIS = 10_000L;

    private final VoiceSessionService voiceSessionService;
    private final VoiceTurnService voiceTurnService;
    private final AzureSpeechV2StreamingClient azureSpeechClient;
    private final ObjectMapper objectMapper;
    private final long resumeGraceMillis;
    private final long resumeGraceNanos;
    private final Object streamLifecycleMonitor = new Object();
    private final Map<String, ActiveStream> activeStreams = new ConcurrentHashMap<>();
    private final Map<String, ActiveStream> activeVoiceStreams = new ConcurrentHashMap<>();

    public VoiceStreamWebSocketHandler(
            VoiceSessionService voiceSessionService,
            VoiceTurnService voiceTurnService,
            AzureSpeechV2StreamingClient azureSpeechClient,
            ObjectMapper objectMapper,
            @Value("${voice.stream.resume-grace-millis:" + DEFAULT_RESUME_GRACE_MILLIS + "}")
                    long resumeGraceMillis) {
        if (resumeGraceMillis <= 0) {
            throw new IllegalArgumentException("voice.stream.resume-grace-millis must be positive.");
        }
        this.voiceSessionService = voiceSessionService;
        this.voiceTurnService = voiceTurnService;
        this.azureSpeechClient = azureSpeechClient;
        this.objectMapper = objectMapper;
        this.resumeGraceMillis = resumeGraceMillis;
        this.resumeGraceNanos = TimeUnit.MILLISECONDS.toNanos(resumeGraceMillis);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        try {
            String userId = userId(session);
            String sessionId = voiceSessionId(session);
            VoiceSessionDetailResponse voiceSession = voiceSessionService.get(userId, sessionId);
            if (voiceSession.getFlowType() != VoiceFlowType.TRANSFER
                    || voiceSession.getSttMode() != SttMode.BACKEND_STREAM) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
        } catch (BusinessException exception) {
            sendErrorAndClose(session, exception.getErrorCode());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode event = objectMapper.readTree(message.getPayload());
            String type = event.path("type").asText();
            String turnId = event.path("turnId").asText();
            if (!isCanonicalUuid(turnId)) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
            if (START.equals(type)) {
                start(session, turnId);
            } else if (RESUME.equals(type)) {
                resume(session, turnId, resumeLastReceivedSequence(event));
            } else if (STOP.equals(type)) {
                stop(session, turnId);
            } else if (BARGE_IN.equals(type)) {
                cancel(session, turnId);
            } else {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            sendError(session, ErrorCode.INVALID_REQUEST);
        } catch (BusinessException exception) {
            sendError(session, exception.getErrorCode());
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        ActiveStream active = activeStreams.get(session.getId());
        if (active == null || active.cancelled.get() || active.stopRequested.get()) {
            sendError(session, ErrorCode.VOICE_TURN_CONFLICT);
            return;
        }

        ByteBuffer frame = message.getPayload().asReadOnlyBuffer().order(ByteOrder.BIG_ENDIAN);
        if (frame.remaining() <= AUDIO_SEQUENCE_HEADER_BYTES) {
            sendError(session, ErrorCode.INVALID_REQUEST);
            return;
        }

        long sequence = Integer.toUnsignedLong(frame.getInt());
        if (frame.remaining() > MAX_PCM_FRAME_BYTES) {
            sendError(session, ErrorCode.INVALID_REQUEST);
            return;
        }
        if (!active.acceptAudioSequence(sequence)) {
            sendError(session, ErrorCode.INVALID_REQUEST);
            return;
        }

        byte[] audio = new byte[frame.remaining()];
        frame.get(audio);
        active.stream.write(audio);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        ActiveStream active = activeStreams.get(session.getId());
        if (active == null) {
            return;
        }

        ConnectionCloseAction action;
        synchronized (streamLifecycleMonitor) {
            if (activeStreams.get(session.getId()) != active) {
                return;
            }
            action = active.onConnectionClosed(session);
            if (action != ConnectionCloseAction.IGNORED) {
                activeStreams.remove(session.getId(), active);
            }
        }
        if (action == ConnectionCloseAction.CLOSE) {
            scheduleCloseActive(active);
            return;
        }
        if (action == ConnectionCloseAction.DETACHED_FOR_RESUME) {
            CompletableFuture.delayedExecutor(resumeGraceMillis, TimeUnit.MILLISECONDS)
                    .execute(() -> closeExpiredDetachedStream(active));
        }
    }

    private void start(WebSocketSession session, String turnId) {
        String userId = userId(session);
        String sessionId = voiceSessionId(session);
        validateStartSession(voiceSessionService.get(userId, sessionId));
        if (activeStreams.containsKey(session.getId()) || activeVoiceStreams.containsKey(sessionId)) {
            throw new BusinessException(ErrorCode.VOICE_TURN_CONFLICT);
        }

        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicBoolean finalHandled = new AtomicBoolean();
        StreamLifecycle lifecycle = new StreamLifecycle();
        AzureSpeechRecognitionStream stream = null;
        ActiveStream active = null;
        try {
            stream = azureSpeechClient.open(new AzureSpeechRecognitionListener() {
                @Override
                public void onPartialTranscript(String transcript) {
                    if (!cancelled.get()) {
                        sendPartial(responseSession(lifecycle.active.get(), session), turnId, transcript);
                    }
                }

                @Override
                public void onFinalResult(AzureSpeechDetailedResult result) {
                    if (cancelled.get() || !finalHandled.compareAndSet(false, true)) {
                        return;
                    }
                    sendFinal(responseSession(lifecycle.active.get(), session), turnId, result);
                    try {
                        VoiceTurnResponse response = voiceTurnService.processAzureTransferFinal(
                                userId, sessionId, turnId, result);
                        sendTurnResponse(responseSession(lifecycle.active.get(), session), turnId, response);
                    } catch (BusinessException exception) {
                        sendError(responseSession(lifecycle.active.get(), session), exception.getErrorCode());
                    } finally {
                        requestCloseActive(lifecycle);
                    }
                }

                @Override
                public void onFailure() {
                    if (!cancelled.get()) {
                        sendError(responseSession(lifecycle.active.get(), session), ErrorCode.SPEECH_RECOGNITION_FAILED);
                    }
                    requestCloseActive(lifecycle);
                }
            });
            active = new ActiveStream(userId, sessionId, turnId, stream, cancelled, session, resumeGraceNanos);
            synchronized (streamLifecycleMonitor) {
                if (!session.isOpen()
                        || activeVoiceStreams.putIfAbsent(sessionId, active) != null
                        || activeStreams.putIfAbsent(session.getId(), active) != null) {
                    activeVoiceStreams.remove(sessionId, active);
                    activeStreams.remove(session.getId(), active);
                    stopAndClose(stream);
                    stream = null;
                    throw new BusinessException(ErrorCode.VOICE_TURN_CONFLICT);
                }
            }
            lifecycle.active.set(active);
            if (lifecycle.cleanupRequested.get()) {
                scheduleCloseActive(active);
            }
        } catch (RuntimeException exception) {
            if (active != null) {
                synchronized (streamLifecycleMonitor) {
                    activeVoiceStreams.remove(sessionId, active);
                    activeStreams.remove(session.getId(), active);
                }
            }
            if (stream != null) {
                stopAndClose(stream);
            }
            throw exception;
        }
    }

    private void validateStartSession(VoiceSessionDetailResponse voiceSession) {
        if (voiceSession.getFlowType() != VoiceFlowType.TRANSFER
                || voiceSession.getSttMode() != SttMode.BACKEND_STREAM
                || voiceSession.getStatus() != VoiceSessionStatus.LISTENING) {
            throw new BusinessException(ErrorCode.VOICE_TURN_CONFLICT);
        }
    }

    private void stop(WebSocketSession session, String turnId) {
        ActiveStream active = requiredActive(session, turnId);
        active.stopRequested.set(true);
        try {
            active.stopRecognition();
        } catch (BusinessException exception) {
            scheduleCloseActive(active);
            throw exception;
        }
    }

    private void cancel(WebSocketSession session, String turnId) {
        ActiveStream active = requiredActive(session, turnId);
        active.cancelled.set(true);
        scheduleCloseActive(active);
        ObjectNode response = objectMapper.createObjectNode();
        response.put("type", "CANCEL_ACK");
        response.put("turnId", turnId);
        send(session, response);
    }

    private ActiveStream requiredActive(WebSocketSession session, String turnId) {
        ActiveStream active = activeStreams.get(session.getId());
        if (active == null || !active.turnId.equals(turnId)) {
            throw new BusinessException(ErrorCode.VOICE_TURN_CONFLICT);
        }
        return active;
    }

    private void resume(WebSocketSession session, String turnId, long lastReceivedSequence) {
        String userId = userId(session);
        String sessionId = voiceSessionId(session);
        validateStartSession(voiceSessionService.get(userId, sessionId));
        ActiveStream active = activeVoiceStreams.get(sessionId);
        if (active == null) {
            throw new BusinessException(ErrorCode.VOICE_TURN_CONFLICT);
        }
        if (!active.matchesUserAndTurn(userId, turnId)) {
            throw new BusinessException(ErrorCode.VOICE_TURN_CONFLICT);
        }
        if (!active.matchesLastReceivedSequence(lastReceivedSequence)) {
            active.cancelled.set(true);
            scheduleCloseActive(active);
            throw new BusinessException(ErrorCode.VOICE_TURN_CONFLICT);
        }
        synchronized (streamLifecycleMonitor) {
            if (activeVoiceStreams.get(sessionId) != active
                    || activeStreams.containsKey(session.getId())
                    || !active.resume(session)) {
                throw new BusinessException(ErrorCode.VOICE_TURN_CONFLICT);
            }
            activeStreams.put(session.getId(), active);
        }
    }

    private long resumeLastReceivedSequence(JsonNode event) {
        JsonNode sequence = event.get("lastReceivedSequence");
        if (sequence == null
                || !sequence.isIntegralNumber()
                || !sequence.canConvertToLong()
                || sequence.longValue() < -1
                || sequence.longValue() > MAX_UNSIGNED_INT) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return sequence.longValue();
    }

    private void sendPartial(WebSocketSession session, String turnId, String transcript) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("type", "PARTIAL_TRANSCRIPT");
        response.put("turnId", turnId);
        response.put("text", transcript);
        send(session, response);
    }

    private void sendFinal(WebSocketSession session, String turnId, AzureSpeechDetailedResult result) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("type", "FINAL_TRANSCRIPT");
        response.put("turnId", turnId);
        response.put("text", result.transcript());
        if (result.confidence() != null) {
            response.put("sttConfidence", result.confidence());
        }
        send(session, response);
    }

    private void sendTurnResponse(WebSocketSession session, String turnId, VoiceTurnResponse result) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("type", "TURN_RESPONSE");
        response.put("turnId", turnId);
        response.set("data", objectMapper.valueToTree(result));
        send(session, response);
    }

    private void sendError(WebSocketSession session, ErrorCode errorCode) {
        String requestId = UUID.randomUUID().toString();
        log.warn("Voice stream WebSocket error. requestId={}, errorCode={}", requestId, errorCode.getCode());
        ObjectNode response = objectMapper.createObjectNode();
        response.put("type", "ERROR");
        response.put("code", errorCode.getCode());
        response.put("message", errorCode.getMessage());
        response.put("retryable", errorCode.isRetryable());
        response.put("requestId", requestId);
        send(session, response);
    }

    private void sendErrorAndClose(WebSocketSession session, ErrorCode errorCode) {
        sendError(session, errorCode);
        try {
            session.close(CloseStatus.POLICY_VIOLATION);
        } catch (IOException exception) {
            log.warn("Unable to close unauthorized voice stream WebSocket.");
        }
    }

    private void send(WebSocketSession session, JsonNode event) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(event)));
            }
        } catch (IOException exception) {
            log.warn("Unable to send voice stream WebSocket event.");
        }
    }

    private void closeActive(ActiveStream expectedActive) {
        if (expectedActive == null) {
            return;
        }
        synchronized (streamLifecycleMonitor) {
            if (!activeVoiceStreams.remove(expectedActive.voiceSessionId, expectedActive)) {
                return;
            }
            WebSocketSession attachedSession = expectedActive.clearAttachedSession();
            if (attachedSession != null) {
                activeStreams.remove(attachedSession.getId(), expectedActive);
            }
        }
        stopAndClose(expectedActive);
    }

    private void scheduleCloseActive(ActiveStream active) {
        if (active != null && active.cleanupScheduled.compareAndSet(false, true)) {
            CompletableFuture.runAsync(() -> closeActive(active));
        }
    }

    private void requestCloseActive(StreamLifecycle lifecycle) {
        lifecycle.cleanupRequested.set(true);
        scheduleCloseActive(lifecycle.active.get());
    }

    private void closeExpiredDetachedStream(ActiveStream active) {
        synchronized (streamLifecycleMonitor) {
            if (active.isResumeExpired()) {
                scheduleCloseActive(active);
            }
        }
    }

    private WebSocketSession responseSession(ActiveStream active, WebSocketSession fallbackSession) {
        if (active == null) {
            return fallbackSession;
        }
        return active.attachedSession();
    }

    private void stopAndClose(AzureSpeechRecognitionStream stream) {
        try {
            stream.stop();
        } catch (BusinessException exception) {
            log.warn("Unable to stop Azure Speech recognition stream before closing.");
        } finally {
            stream.close();
        }
    }

    private void stopAndClose(ActiveStream active) {
        try {
            active.stopRecognition();
        } catch (BusinessException exception) {
            log.warn("Unable to stop Azure Speech recognition stream before closing.");
        } finally {
            active.closeRecognition();
        }
    }

    private String userId(WebSocketSession session) {
        Principal principal = session.getPrincipal();
        if (principal == null || !isCanonicalUuid(principal.getName())) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
        return principal.getName();
    }

    private String voiceSessionId(WebSocketSession session) {
        if (session.getUri() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Matcher matcher = SESSION_PATH.matcher(session.getUri().getPath());
        if (!matcher.matches()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return matcher.group(1);
    }

    private boolean isCanonicalUuid(String value) {
        return value != null && value.matches(VoiceIdentifierPattern.CANONICAL_UUID_REGEX);
    }

    private static class ActiveStream {
        private final String userId;
        private final String voiceSessionId;
        private final String turnId;
        private final AzureSpeechRecognitionStream stream;
        private final AtomicBoolean cancelled;
        private final long resumeGraceNanos;
        private final AtomicBoolean stopRequested = new AtomicBoolean();
        private final AtomicBoolean recognitionStopped = new AtomicBoolean();
        private final AtomicBoolean recognitionClosed = new AtomicBoolean();
        private final AtomicBoolean cleanupScheduled = new AtomicBoolean();
        private long nextAudioSequence;
        private WebSocketSession attachedSession;
        private long detachedAtNanos;

        private ActiveStream(
                String userId,
                String voiceSessionId,
                String turnId,
                AzureSpeechRecognitionStream stream,
                AtomicBoolean cancelled,
                WebSocketSession attachedSession,
                long resumeGraceNanos) {
            this.userId = userId;
            this.voiceSessionId = voiceSessionId;
            this.turnId = turnId;
            this.stream = stream;
            this.cancelled = cancelled;
            this.attachedSession = attachedSession;
            this.resumeGraceNanos = resumeGraceNanos;
        }

        private synchronized boolean acceptAudioSequence(long sequence) {
            if (nextAudioSequence > MAX_UNSIGNED_INT || sequence != nextAudioSequence) {
                return false;
            }
            nextAudioSequence++;
            return true;
        }

        private synchronized void stopRecognition() {
            if (recognitionStopped.compareAndSet(false, true)) {
                stream.stop();
            }
        }

        private synchronized void closeRecognition() {
            if (recognitionClosed.compareAndSet(false, true)) {
                stream.close();
            }
        }

        private synchronized ConnectionCloseAction onConnectionClosed(WebSocketSession session) {
            if (attachedSession != session) {
                return ConnectionCloseAction.IGNORED;
            }
            if (cancelled.get() || stopRequested.get() || cleanupScheduled.get()) {
                return ConnectionCloseAction.CLOSE;
            }
            attachedSession = null;
            detachedAtNanos = System.nanoTime();
            return ConnectionCloseAction.DETACHED_FOR_RESUME;
        }

        private synchronized boolean matchesUserAndTurn(String userId, String turnId) {
            return this.userId.equals(userId) && this.turnId.equals(turnId);
        }

        private synchronized boolean matchesLastReceivedSequence(long lastReceivedSequence) {
            return lastReceivedSequence == nextAudioSequence - 1;
        }

        private synchronized boolean resume(WebSocketSession session) {
            if (cancelled.get()
                    || stopRequested.get()
                    || cleanupScheduled.get()
                    || attachedSession != null
                    || isResumeExpired()) {
                return false;
            }
            attachedSession = session;
            detachedAtNanos = 0;
            return true;
        }

        private synchronized WebSocketSession attachedSession() {
            return attachedSession;
        }

        private synchronized WebSocketSession clearAttachedSession() {
            WebSocketSession currentSession = attachedSession;
            attachedSession = null;
            detachedAtNanos = 0;
            return currentSession;
        }

        private synchronized boolean isResumeExpired() {
            return attachedSession == null
                    && detachedAtNanos != 0
                    && System.nanoTime() - detachedAtNanos >= resumeGraceNanos;
        }
    }

    private enum ConnectionCloseAction {
        IGNORED,
        DETACHED_FOR_RESUME,
        CLOSE
    }

    private static class StreamLifecycle {
        private final AtomicBoolean cleanupRequested = new AtomicBoolean();
        private final AtomicReference<ActiveStream> active = new AtomicReference<>();
    }
}
