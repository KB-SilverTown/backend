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
import com.silvertown.domain.voice.service.VoiceStreamLifecycleService;
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
    private final VoiceStreamLifecycleService voiceStreamLifecycleService;
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
            VoiceStreamLifecycleService voiceStreamLifecycleService,
            VoiceTurnService voiceTurnService,
            AzureSpeechV2StreamingClient azureSpeechClient,
            ObjectMapper objectMapper,
            @Value("${voice.stream.resume-grace-millis:" + DEFAULT_RESUME_GRACE_MILLIS + "}")
                    long resumeGraceMillis) {
        if (resumeGraceMillis <= 0) {
            throw new IllegalArgumentException("voice.stream.resume-grace-millis must be positive.");
        }
        this.voiceSessionService = voiceSessionService;
        this.voiceStreamLifecycleService = voiceStreamLifecycleService;
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
        } catch (RuntimeException exception) {
            closeUnexpectedServerError(session);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode event = objectMapper.readTree(message.getPayload());
            String type = event.path("type").asText();
            if (START.equals(type)) {
                start(session, inputTurnId(event));
            } else if (RESUME.equals(type)) {
                resume(session, inputTurnId(event), resumeLastReceivedSequence(event));
            } else if (STOP.equals(type)) {
                stop(session, inputTurnId(event));
            } else if (BARGE_IN.equals(type)) {
                bargeIn(session, event);
            } else {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            sendError(session, ErrorCode.INVALID_REQUEST);
        } catch (BusinessException exception) {
            sendError(session, exception.getErrorCode());
        } catch (RuntimeException exception) {
            closeUnexpectedServerError(session);
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        try {
            ActiveStream active = activeStreams.get(session.getId());
            if (active == null
                    || !active.startAcknowledged.get()
                    || active.cancelled.get()
                    || active.stopRequested.get()) {
                sendError(session, ErrorCode.VOICE_TURN_CONFLICT);
                return;
            }

            ByteBuffer frame = message.getPayload().asReadOnlyBuffer().order(ByteOrder.BIG_ENDIAN);
            if (frame.remaining() <= AUDIO_SEQUENCE_HEADER_BYTES) {
                rejectInvalidPcm(session, active);
                return;
            }

            long sequence = Integer.toUnsignedLong(frame.getInt());
            if (frame.remaining() > MAX_PCM_FRAME_BYTES || !active.acceptAudioSequence(sequence)) {
                rejectInvalidPcm(session, active);
                return;
            }

            byte[] audio = new byte[frame.remaining()];
            frame.get(audio);
            active.stream.write(audio);
        } catch (BusinessException exception) {
            sendError(session, exception.getErrorCode());
        } catch (RuntimeException exception) {
            closeUnexpectedServerError(session);
        }
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

    private void start(WebSocketSession session, String inputTurnId) {
        String userId = userId(session);
        String sessionId = voiceSessionId(session);
        if (activeStreams.containsKey(session.getId()) || hasActiveInputForSession(sessionId)) {
            throw new BusinessException(ErrorCode.VOICE_TURN_CONFLICT);
        }

        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicBoolean finalHandled = new AtomicBoolean();
        StreamLifecycle lifecycle = new StreamLifecycle();
        AzureSpeechRecognitionStream stream = null;
        ActiveStream active = null;
        long lifecycleGeneration = voiceStreamLifecycleService.claimInputTurn(userId, sessionId, inputTurnId);
        try {
            stream = azureSpeechClient.open(new AzureSpeechRecognitionListener() {
                @Override
                public void onPartialTranscript(String transcript) {
                    if (!cancelled.get()) {
                        sendPartial(responseSession(lifecycle.active.get(), session), inputTurnId, transcript);
                    }
                }

                @Override
                public void onFinalResult(AzureSpeechDetailedResult result) {
                    if (cancelled.get() || !finalHandled.compareAndSet(false, true)) {
                        return;
                    }
                    try {
                        voiceStreamLifecycleService.beginFinalProcessing(
                                userId, sessionId, inputTurnId, lifecycleGeneration);
                        if (cancelled.get()) {
                            return;
                        }
                        VoiceTurnResponse response = voiceTurnService.processAzureTransferFinal(
                                userId, sessionId, inputTurnId, lifecycleGeneration, result);
                        if (!cancelled.get()) {
                            sendFinal(responseSession(lifecycle.active.get(), session), inputTurnId, result);
                            sendTurnResponse(responseSession(lifecycle.active.get(), session), inputTurnId, response);
                        }
                    } catch (BusinessException exception) {
                        if (!cancelled.get() && exception.getErrorCode() != ErrorCode.VOICE_TURN_CONFLICT) {
                            sendError(responseSession(lifecycle.active.get(), session), exception.getErrorCode());
                            if (exception.getErrorCode().isRetryable()) {
                                cancelInputLifecycle(userId, sessionId, inputTurnId, lifecycleGeneration);
                            }
                        }
                    } catch (RuntimeException exception) {
                        cancelled.set(true);
                        cancelInputLifecycle(userId, sessionId, inputTurnId, lifecycleGeneration);
                        closeUnexpectedServerError(responseSession(lifecycle.active.get(), session));
                    } finally {
                        requestCloseActive(lifecycle);
                    }
                }

                @Override
                public void onFailure() {
                    if (!cancelled.get()) {
                        sendError(responseSession(lifecycle.active.get(), session), ErrorCode.SPEECH_RECOGNITION_FAILED);
                        cancelInputLifecycle(userId, sessionId, inputTurnId, lifecycleGeneration);
                    }
                    requestCloseActive(lifecycle);
                }
            });
            active = new ActiveStream(
                    userId,
                    sessionId,
                    inputTurnId,
                    lifecycleGeneration,
                    stream,
                    cancelled,
                    session,
                    resumeGraceNanos);
            synchronized (streamLifecycleMonitor) {
                if (!session.isOpen()
                        || activeVoiceStreams.putIfAbsent(streamKey(sessionId, inputTurnId), active) != null
                        || activeStreams.putIfAbsent(session.getId(), active) != null) {
                    activeVoiceStreams.remove(streamKey(sessionId, inputTurnId), active);
                    activeStreams.remove(session.getId(), active);
                    stopAndClose(stream);
                    stream = null;
                    throw new BusinessException(ErrorCode.VOICE_TURN_CONFLICT);
                }
            }
            lifecycle.active.set(active);
            active.startAcknowledged.set(true);
            sendStartAck(session, inputTurnId);
            if (lifecycle.cleanupRequested.get()) {
                scheduleCloseActive(active);
            }
        } catch (RuntimeException exception) {
            if (active != null) {
                synchronized (streamLifecycleMonitor) {
                    activeVoiceStreams.remove(streamKey(sessionId, inputTurnId), active);
                    activeStreams.remove(session.getId(), active);
                }
            }
            if (stream != null) {
                stopAndClose(stream);
            }
            cancelInputLifecycle(userId, sessionId, inputTurnId, lifecycleGeneration);
            throw exception;
        }
    }

    private void stop(WebSocketSession session, String inputTurnId) {
        ActiveStream active = requiredActive(session, inputTurnId);
        active.stopRequested.set(true);
        try {
            active.stopRecognition();
            sendStopAck(session, inputTurnId);
        } catch (BusinessException exception) {
            active.cancelled.set(true);
            cancelInputLifecycle(active);
            scheduleCloseActive(active);
            throw exception;
        }
    }

    private void bargeIn(WebSocketSession session, JsonNode event) {
        String target = event.path("target").asText();
        if ("AI_TTS".equals(target)) {
            String interruptedAiTurnId = requiredIdentifier(event, "interruptedAiTurnId");
            voiceStreamLifecycleService.interruptAiTts(
                    userId(session), voiceSessionId(session), interruptedAiTurnId);
            closeActiveStreamForSession(voiceSessionId(session));
            sendCancelledAi(session, interruptedAiTurnId);
            return;
        }
        if ("INPUT_STREAM".equals(target)) {
            String inputTurnId = inputTurnId(event);
            ActiveStream active = requiredActive(session, inputTurnId);
            active.cancelled.set(true);
            try {
                voiceStreamLifecycleService.cancelInputStream(
                        active.userId,
                        active.voiceSessionId,
                        active.inputTurnId,
                        active.lifecycleGeneration);
            } catch (BusinessException exception) {
                active.cancelled.set(false);
                throw exception;
            }
            closeActive(active);
            sendCancelledInput(session, inputTurnId);
            return;
        }
        throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }

    private ActiveStream requiredActive(WebSocketSession session, String inputTurnId) {
        ActiveStream active = activeStreams.get(session.getId());
        if (active == null || !active.inputTurnId.equals(inputTurnId)) {
            throw new BusinessException(ErrorCode.VOICE_TURN_CONFLICT);
        }
        return active;
    }

    private void resume(WebSocketSession session, String inputTurnId, long lastReceivedSequence) {
        String userId = userId(session);
        String sessionId = voiceSessionId(session);
        ActiveStream active = activeVoiceStreams.get(streamKey(sessionId, inputTurnId));
        if (active == null) {
            throw new BusinessException(ErrorCode.VOICE_TURN_CONFLICT);
        }
        if (!active.matchesUserAndTurn(userId, inputTurnId)) {
            throw new BusinessException(ErrorCode.VOICE_TURN_CONFLICT);
        }
        if (!active.matchesLastReceivedSequence(lastReceivedSequence)) {
            active.cancelled.set(true);
            try {
                cancelInputLifecycle(active);
            } finally {
                scheduleCloseActive(active);
            }
            throw new BusinessException(ErrorCode.VOICE_TURN_CONFLICT);
        }
        synchronized (streamLifecycleMonitor) {
            if (activeVoiceStreams.get(streamKey(sessionId, inputTurnId)) != active
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

    private void sendStartAck(WebSocketSession session, String inputTurnId) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("type", "START_ACK");
        response.put("inputTurnId", inputTurnId);
        response.put("nextSequence", 0);
        send(session, response);
    }

    private void sendStopAck(WebSocketSession session, String inputTurnId) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("type", "STOP_ACK");
        response.put("inputTurnId", inputTurnId);
        send(session, response);
    }

    private void sendCancelledAi(WebSocketSession session, String interruptedAiTurnId) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("type", "CANCELLED");
        response.put("target", "AI_TTS");
        response.put("interruptedAiTurnId", interruptedAiTurnId);
        response.put("readyForStart", true);
        send(session, response);
    }

    private void sendCancelledInput(WebSocketSession session, String inputTurnId) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("type", "CANCELLED");
        response.put("target", "INPUT_STREAM");
        response.put("inputTurnId", inputTurnId);
        response.put("readyForStart", true);
        send(session, response);
    }

    private void sendPartial(WebSocketSession session, String inputTurnId, String transcript) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("type", "PARTIAL_TRANSCRIPT");
        response.put("inputTurnId", inputTurnId);
        response.put("text", transcript);
        send(session, response);
    }

    private void sendFinal(WebSocketSession session, String inputTurnId, AzureSpeechDetailedResult result) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("type", "FINAL_TRANSCRIPT");
        response.put("inputTurnId", inputTurnId);
        response.put("text", result.transcript());
        if (result.confidence() != null) {
            response.put("sttConfidence", result.confidence());
        }
        send(session, response);
    }

    private void sendTurnResponse(WebSocketSession session, String inputTurnId, VoiceTurnResponse result) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("type", "TURN_RESPONSE");
        response.put("inputTurnId", inputTurnId);
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
            if (!activeVoiceStreams.remove(
                    streamKey(expectedActive.voiceSessionId, expectedActive.inputTurnId), expectedActive)) {
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
        boolean expired = false;
        synchronized (streamLifecycleMonitor) {
            if (activeVoiceStreams.get(streamKey(active.voiceSessionId, active.inputTurnId)) == active
                    && active.isResumeExpired()) {
                active.cancelled.set(true);
                expired = true;
            }
        }
        if (!expired) {
            return;
        }
        try {
            cancelInputLifecycle(active);
        } finally {
            scheduleCloseActive(active);
        }
    }

    private void closeUnexpectedServerError(WebSocketSession session) {
        ActiveStream active = session == null ? null : activeStreams.get(session.getId());
        if (active != null) {
            active.cancelled.set(true);
            try {
                cancelInputLifecycle(active);
            } finally {
                closeActive(active);
            }
        }
        sendError(session, ErrorCode.INTERNAL_SERVER_ERROR);
        if (session == null) {
            return;
        }
        try {
            session.close(CloseStatus.SERVER_ERROR);
        } catch (IOException exception) {
            log.warn("Unable to close failed voice stream WebSocket.");
        }
    }

    private void rejectInvalidPcm(WebSocketSession session, ActiveStream active) {
        active.cancelled.set(true);
        try {
            cancelInputLifecycle(active);
        } finally {
            closeActive(active);
        }
        sendError(session, ErrorCode.INVALID_REQUEST);
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

    private String inputTurnId(JsonNode event) {
        String inputTurnId = event.path("inputTurnId").asText();
        if (inputTurnId.isBlank()) {
            inputTurnId = event.path("turnId").asText();
        }
        if (!isCanonicalUuid(inputTurnId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return inputTurnId;
    }

    private String requiredIdentifier(JsonNode event, String fieldName) {
        String value = event.path(fieldName).asText();
        if (!isCanonicalUuid(value)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return value;
    }

    private String streamKey(String sessionId, String inputTurnId) {
        return sessionId + ':' + inputTurnId;
    }

    private boolean hasActiveInputForSession(String sessionId) {
        return activeVoiceStreams.values().stream()
                .anyMatch(active -> active.voiceSessionId.equals(sessionId));
    }

    private void closeActiveStreamForSession(String sessionId) {
        activeVoiceStreams.values().stream()
                .filter(active -> active.voiceSessionId.equals(sessionId))
                .forEach(active -> {
                    active.cancelled.set(true);
                    closeActive(active);
                });
    }

    private void cancelInputLifecycle(ActiveStream active) {
        cancelInputLifecycle(
                active.userId, active.voiceSessionId, active.inputTurnId, active.lifecycleGeneration);
    }

    private void cancelInputLifecycle(
            String userId, String sessionId, String inputTurnId, long lifecycleGeneration) {
        try {
            voiceStreamLifecycleService.cancelInputStream(userId, sessionId, inputTurnId, lifecycleGeneration);
        } catch (BusinessException exception) {
            if (exception.getErrorCode() != ErrorCode.VOICE_TURN_CONFLICT) {
                throw exception;
            }
        }
    }

    private static class ActiveStream {
        private final String userId;
        private final String voiceSessionId;
        private final String inputTurnId;
        private final long lifecycleGeneration;
        private final AzureSpeechRecognitionStream stream;
        private final AtomicBoolean cancelled;
        private final long resumeGraceNanos;
        private final AtomicBoolean stopRequested = new AtomicBoolean();
        private final AtomicBoolean recognitionStopped = new AtomicBoolean();
        private final AtomicBoolean recognitionClosed = new AtomicBoolean();
        private final AtomicBoolean cleanupScheduled = new AtomicBoolean();
        private final AtomicBoolean startAcknowledged = new AtomicBoolean();
        private long nextAudioSequence;
        private WebSocketSession attachedSession;
        private long detachedAtNanos;

        private ActiveStream(
                String userId,
                String voiceSessionId,
                String inputTurnId,
                long lifecycleGeneration,
                AzureSpeechRecognitionStream stream,
                AtomicBoolean cancelled,
                WebSocketSession attachedSession,
                long resumeGraceNanos) {
            this.userId = userId;
            this.voiceSessionId = voiceSessionId;
            this.inputTurnId = inputTurnId;
            this.lifecycleGeneration = lifecycleGeneration;
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

        private synchronized boolean matchesUserAndTurn(String userId, String inputTurnId) {
            return this.userId.equals(userId) && this.inputTurnId.equals(inputTurnId);
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
