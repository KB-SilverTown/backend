package com.silvertown.domain.voice.websocket;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvertown.domain.voice.dto.VoiceSessionDetailResponse;
import com.silvertown.domain.voice.dto.VoiceTurnResponse;
import com.silvertown.domain.voice.enums.DialogueStep;
import com.silvertown.domain.voice.enums.SttMode;
import com.silvertown.domain.voice.enums.VoiceFlowType;
import com.silvertown.domain.voice.enums.VoiceSessionEntryPoint;
import com.silvertown.domain.voice.enums.VoiceSessionStatus;
import com.silvertown.domain.voice.service.VoiceSessionService;
import com.silvertown.domain.voice.service.VoiceStreamLifecycleService;
import com.silvertown.domain.voice.service.VoiceTurnService;
import com.silvertown.domain.voice.stt.AzureSpeechDetailedResult;
import com.silvertown.domain.voice.stt.AzureSpeechRecognitionListener;
import com.silvertown.domain.voice.stt.AzureSpeechRecognitionStream;
import com.silvertown.domain.voice.stt.AzureSpeechV2StreamingClient;
import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import java.net.URI;
import java.security.Principal;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class VoiceStreamWebSocketHandlerTest {
    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String SESSION_ID = "10000000-0000-0000-0000-000000000001";
    private static final String FIRST_TURN_ID = "20000000-0000-0000-0000-000000000001";
    private static final String SECOND_TURN_ID = "20000000-0000-0000-0000-000000000002";
    private static final long DEFAULT_RESUME_GRACE_MILLIS = 10_000L;
    private static final int MAX_PCM_FRAME_BYTES = 64 * 1024;

    private VoiceSessionService voiceSessionService;
    private VoiceStreamLifecycleService voiceStreamLifecycleService;
    private VoiceTurnService voiceTurnService;
    private AzureSpeechV2StreamingClient azureSpeechClient;
    private AzureSpeechRecognitionStream stream;
    private VoiceStreamWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        voiceSessionService = org.mockito.Mockito.mock(VoiceSessionService.class);
        voiceStreamLifecycleService = org.mockito.Mockito.mock(VoiceStreamLifecycleService.class);
        voiceTurnService = org.mockito.Mockito.mock(VoiceTurnService.class);
        azureSpeechClient = org.mockito.Mockito.mock(AzureSpeechV2StreamingClient.class);
        stream = org.mockito.Mockito.mock(AzureSpeechRecognitionStream.class);
        handler = newHandler(DEFAULT_RESUME_GRACE_MILLIS);
    }

    @Test
    void rejectsStartForANonListeningSessionBeforeOpeningAzureSpeech() throws Exception {
        WebSocketSession session = webSocketSession("websocket-1");
        when(voiceSessionService.get(USER_ID, SESSION_ID)).thenReturn(session(VoiceSessionStatus.PROCESSING));
        doThrow(new BusinessException(ErrorCode.VOICE_TURN_CONFLICT))
                .when(voiceStreamLifecycleService)
                .claimInputTurn(USER_ID, SESSION_ID, FIRST_TURN_ID);

        handler.handleMessage(session, start(FIRST_TURN_ID));

        verify(azureSpeechClient, never()).open(any());
    }

    @Test
    void permitsOnlyOneActiveStreamForTheSameVoiceSession() throws Exception {
        WebSocketSession firstSession = webSocketSession("websocket-1");
        WebSocketSession secondSession = webSocketSession("websocket-2");
        when(voiceSessionService.get(USER_ID, SESSION_ID)).thenReturn(session(VoiceSessionStatus.LISTENING));
        when(azureSpeechClient.open(any())).thenReturn(stream);

        handler.handleMessage(firstSession, start(FIRST_TURN_ID));
        handler.handleMessage(secondSession, start(SECOND_TURN_ID));

        verify(azureSpeechClient, times(1)).open(any());
    }

    @Test
    void closesAzureResourcesOutsideTheSdkFailureCallback() throws Exception {
        WebSocketSession session = webSocketSession("websocket-1");
        ArgumentCaptor<AzureSpeechRecognitionListener> listener =
                ArgumentCaptor.forClass(AzureSpeechRecognitionListener.class);
        when(voiceSessionService.get(USER_ID, SESSION_ID)).thenReturn(session(VoiceSessionStatus.LISTENING));
        when(azureSpeechClient.open(listener.capture())).thenReturn(stream);

        handler.handleMessage(session, start(FIRST_TURN_ID));
        listener.getValue().onFailure();

        verify(stream, timeout(1_000)).stop();
        verify(stream, timeout(1_000)).close();
    }

    @Test
    void marksSpeechRecognitionFailureAsRetryable() throws Exception {
        WebSocketSession session = webSocketSession("websocket-1");
        ArgumentCaptor<AzureSpeechRecognitionListener> listener =
                ArgumentCaptor.forClass(AzureSpeechRecognitionListener.class);
        when(voiceSessionService.get(USER_ID, SESSION_ID)).thenReturn(session(VoiceSessionStatus.LISTENING));
        when(azureSpeechClient.open(listener.capture())).thenReturn(stream);

        handler.handleMessage(session, start(FIRST_TURN_ID));
        listener.getValue().onFailure();

        ArgumentCaptor<TextMessage> error = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, timeout(1_000).atLeast(2)).sendMessage(error.capture());
        com.fasterxml.jackson.databind.JsonNode payload = error.getAllValues().stream()
                .map(message -> readJson(message.getPayload()))
                .filter(message -> "ERROR".equals(message.path("type").asText()))
                .findFirst()
                .orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("SPEECH_RECOGNITION_FAILED", payload.path("code").asText());
        org.junit.jupiter.api.Assertions.assertTrue(payload.path("retryable").asBoolean());
    }

    @Test
    void returnsTheSameCorrelationFieldsInsideAndOutsideTurnResponse() throws Exception {
        WebSocketSession session = webSocketSession("websocket-1");
        ArgumentCaptor<AzureSpeechRecognitionListener> listener =
                ArgumentCaptor.forClass(AzureSpeechRecognitionListener.class);
        when(voiceSessionService.get(USER_ID, SESSION_ID)).thenReturn(session(VoiceSessionStatus.LISTENING));
        when(azureSpeechClient.open(listener.capture())).thenReturn(stream);
        when(voiceTurnService.processAzureTransferFinal(any(), any(), any(), any(Long.class), any())).thenReturn(
                new VoiceTurnResponse(
                        SESSION_ID,
                        FIRST_TURN_ID,
                        DialogueStep.AWAITING_AMOUNT,
                        "TRANSFER",
                        "TRANSFER_RECIPIENT_CANDIDATES",
                        java.util.Map.of(),
                        new BigDecimal("0.95"),
                        "보낼 금액을 말씀해 주세요.",
                        "<speak>보낼 금액을 말씀해 주세요.</speak>",
                        null,
                        null,
                        null,
                        "ASK_AMOUNT"));

        handler.handleMessage(session, start(FIRST_TURN_ID));
        listener.getValue().onFinalResult(new AzureSpeechDetailedResult(
                "김철수에게 오만 원 보내줘", new BigDecimal("0.95"), List.of()));

        ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, timeout(1_000).times(3)).sendMessage(messages.capture());
        com.fasterxml.jackson.databind.JsonNode turnResponse = messages.getAllValues().stream()
                .map(message -> readJson(message.getPayload()))
                .filter(message -> "TURN_RESPONSE".equals(message.path("type").asText()))
                .findFirst()
                .orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(FIRST_TURN_ID, turnResponse.path("inputTurnId").asText());
        org.junit.jupiter.api.Assertions.assertEquals(SESSION_ID, turnResponse.path("data").path("sessionId").asText());
        org.junit.jupiter.api.Assertions.assertEquals(FIRST_TURN_ID, turnResponse.path("data").path("turnId").asText());
        org.junit.jupiter.api.Assertions.assertEquals(
                "TRANSFER_RECIPIENT_CANDIDATES",
                turnResponse.path("data").path("requestedFunction").asText());
    }

    @Test
    void acknowledgesStartBeforeAcceptingPcmWithTheInputTurnId() throws Exception {
        WebSocketSession session = webSocketSession("websocket-1");
        when(azureSpeechClient.open(any())).thenReturn(stream);

        handler.handleMessage(session, new TextMessage(
                "{\"type\":\"START\",\"inputTurnId\":\"" + FIRST_TURN_ID + "\"}"));

        ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(messages.capture());
        com.fasterxml.jackson.databind.JsonNode ack = readJson(messages.getValue().getPayload());
        org.junit.jupiter.api.Assertions.assertEquals("START_ACK", ack.path("type").asText());
        org.junit.jupiter.api.Assertions.assertEquals(FIRST_TURN_ID, ack.path("inputTurnId").asText());
        org.junit.jupiter.api.Assertions.assertEquals(0, ack.path("nextSequence").asInt());

        handler.handleMessage(session, audio(0, new byte[] {1}));
        verify(stream).write(new byte[] {1});
    }

    @Test
    void acknowledgesStopOnlyAfterPcmIsBlocked() throws Exception {
        WebSocketSession session = webSocketSession("websocket-1");
        when(azureSpeechClient.open(any())).thenReturn(stream);

        handler.handleMessage(session, start(FIRST_TURN_ID));
        handler.handleMessage(session, new TextMessage(
                "{\"type\":\"STOP\",\"inputTurnId\":\"" + FIRST_TURN_ID + "\"}"));
        handler.handleMessage(session, audio(0, new byte[] {1}));

        verify(stream).stop();
        verify(stream, never()).write(any());
        ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, atLeastOnce()).sendMessage(messages.capture());
        org.junit.jupiter.api.Assertions.assertTrue(messages.getAllValues().stream()
                .map(message -> readJson(message.getPayload()))
                .anyMatch(event -> "STOP_ACK".equals(event.path("type").asText())
                        && FIRST_TURN_ID.equals(event.path("inputTurnId").asText())));
    }

    @Test
    void interruptsTheActiveAiTurnThenSignalsReadyForTheNextStart() throws Exception {
        WebSocketSession session = webSocketSession("websocket-1");

        handler.handleMessage(session, new TextMessage("{\"type\":\"BARGE_IN\",\"target\":\"AI_TTS\","
                + "\"interruptedAiTurnId\":\"" + SECOND_TURN_ID + "\"}"));

        verify(voiceStreamLifecycleService).interruptAiTts(USER_ID, SESSION_ID, SECOND_TURN_ID);
        ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(messages.capture());
        com.fasterxml.jackson.databind.JsonNode cancelled = readJson(messages.getValue().getPayload());
        org.junit.jupiter.api.Assertions.assertEquals("CANCELLED", cancelled.path("type").asText());
        org.junit.jupiter.api.Assertions.assertEquals("AI_TTS", cancelled.path("target").asText());
        org.junit.jupiter.api.Assertions.assertTrue(cancelled.path("readyForStart").asBoolean());
    }

    @Test
    void cancelsInputStreamBeforeSendingReadyForTheNextStart() throws Exception {
        WebSocketSession session = webSocketSession("websocket-1");
        when(azureSpeechClient.open(any())).thenReturn(stream);

        handler.handleMessage(session, start(FIRST_TURN_ID));
        handler.handleMessage(session, bargeIn(FIRST_TURN_ID));

        verify(stream).stop();
        verify(stream).close();
        verify(voiceStreamLifecycleService).cancelInputStream(USER_ID, SESSION_ID, FIRST_TURN_ID, 0L);
        ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, atLeastOnce()).sendMessage(messages.capture());
        org.junit.jupiter.api.Assertions.assertTrue(messages.getAllValues().stream()
                .map(message -> readJson(message.getPayload()))
                .anyMatch(event -> "CANCELLED".equals(event.path("type").asText())
                        && "INPUT_STREAM".equals(event.path("target").asText())
                        && event.path("readyForStart").asBoolean()));
    }

    @Test
    void closesAStreamWhoseFinalCallbackCompletesBeforeRegistration() throws Exception {
        WebSocketSession session = webSocketSession("websocket-1");
        when(voiceSessionService.get(USER_ID, SESSION_ID)).thenReturn(session(VoiceSessionStatus.LISTENING));
        when(azureSpeechClient.open(any())).thenAnswer(invocation -> {
            AzureSpeechRecognitionListener listener = invocation.getArgument(0);
            listener.onFinalResult(new AzureSpeechDetailedResult(
                    "오만 원 보내줘", new BigDecimal("0.95"), List.of()));
            return stream;
        });

        handler.handleMessage(session, start(FIRST_TURN_ID));

        verify(stream, timeout(1_000)).stop();
        verify(stream, timeout(1_000)).close();
    }

    @Test
    void forwardsOnlyPcmPayloadAfterTheSequenceHeader() throws Exception {
        WebSocketSession session = webSocketSession("websocket-1");
        byte[] pcm = {10, 20, 30};
        when(voiceSessionService.get(USER_ID, SESSION_ID)).thenReturn(session(VoiceSessionStatus.LISTENING));
        when(azureSpeechClient.open(any())).thenReturn(stream);

        handler.handleMessage(session, start(FIRST_TURN_ID));
        handler.handleMessage(session, audio(0, pcm));

        verify(stream).write(pcm);
    }

    @Test
    void rejectsDuplicateOrMissingAudioSequence() throws Exception {
        WebSocketSession session = webSocketSession("websocket-1");
        when(voiceSessionService.get(USER_ID, SESSION_ID)).thenReturn(session(VoiceSessionStatus.LISTENING));
        when(azureSpeechClient.open(any())).thenReturn(stream);

        handler.handleMessage(session, start(FIRST_TURN_ID));
        handler.handleMessage(session, audio(1, new byte[] {1}));

        verify(stream, never()).write(any());
        assertInvalidRequestErrorWithRequestId(session);
    }

    @Test
    void rejectsDuplicateAudioSequenceAfterAcceptingTheFirstFrame() throws Exception {
        WebSocketSession session = webSocketSession("websocket-1");
        when(voiceSessionService.get(USER_ID, SESSION_ID)).thenReturn(session(VoiceSessionStatus.LISTENING));
        when(azureSpeechClient.open(any())).thenReturn(stream);

        handler.handleMessage(session, start(FIRST_TURN_ID));
        handler.handleMessage(session, audio(0, new byte[] {1}));
        handler.handleMessage(session, audio(0, new byte[] {2}));

        verify(stream).write(new byte[] {1});
        assertInvalidRequestErrorWithRequestId(session);
    }

    @Test
    void rejectsAnOversizedPcmFrameBeforeWritingToAzureSpeech() throws Exception {
        WebSocketSession session = webSocketSession("websocket-1");
        when(voiceSessionService.get(USER_ID, SESSION_ID)).thenReturn(session(VoiceSessionStatus.LISTENING));
        when(azureSpeechClient.open(any())).thenReturn(stream);

        handler.handleMessage(session, start(FIRST_TURN_ID));
        handler.handleMessage(session, audio(0, new byte[MAX_PCM_FRAME_BYTES + 1]));

        verify(stream, never()).write(any());
        assertInvalidRequestErrorWithRequestId(session);
    }

    @Test
    void discardsAzureFinalResultAfterBargeIn() throws Exception {
        WebSocketSession session = webSocketSession("websocket-1");
        ArgumentCaptor<AzureSpeechRecognitionListener> listener =
                ArgumentCaptor.forClass(AzureSpeechRecognitionListener.class);
        when(voiceSessionService.get(USER_ID, SESSION_ID)).thenReturn(session(VoiceSessionStatus.LISTENING));
        when(azureSpeechClient.open(listener.capture())).thenReturn(stream);

        handler.handleMessage(session, start(FIRST_TURN_ID));
        handler.handleMessage(session, bargeIn(FIRST_TURN_ID));
        listener.getValue().onFinalResult(new AzureSpeechDetailedResult(
                "오만 원 보내줘", new BigDecimal("0.95"), List.of()));

        verify(voiceTurnService, never())
                .processAzureTransferFinal(
                        ArgumentMatchers.anyString(),
                        ArgumentMatchers.anyString(),
                        ArgumentMatchers.anyString(),
                        ArgumentMatchers.anyLong(),
                        any());
    }

    @Test
    void suppressesFinalAndTurnResponseWhenBargeInWinsWhileAzureFinalIsWaiting() throws Exception {
        WebSocketSession session = webSocketSession("websocket-1");
        ArgumentCaptor<AzureSpeechRecognitionListener> listener =
                ArgumentCaptor.forClass(AzureSpeechRecognitionListener.class);
        CountDownLatch finalStarted = new CountDownLatch(1);
        CountDownLatch releaseFinal = new CountDownLatch(1);
        when(azureSpeechClient.open(listener.capture())).thenReturn(stream);
        org.mockito.Mockito.doAnswer(invocation -> {
            finalStarted.countDown();
            await(releaseFinal);
            return null;
        }).when(voiceStreamLifecycleService).beginFinalProcessing(USER_ID, SESSION_ID, FIRST_TURN_ID, 0L);

        handler.handleMessage(session, start(FIRST_TURN_ID));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> finalCallback = executor.submit(() -> listener.getValue().onFinalResult(
                    new AzureSpeechDetailedResult("오만 원 보내줘", new BigDecimal("0.95"), List.of())));
            org.junit.jupiter.api.Assertions.assertTrue(finalStarted.await(1, TimeUnit.SECONDS));

            handler.handleMessage(session, bargeIn(FIRST_TURN_ID));
            releaseFinal.countDown();
            finalCallback.get(1, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        verify(voiceTurnService, never()).processAzureTransferFinal(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyLong(),
                any());
        ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, atLeastOnce()).sendMessage(messages.capture());
        org.junit.jupiter.api.Assertions.assertFalse(messages.getAllValues().stream()
                .map(message -> readJson(message.getPayload()).path("type").asText())
                .anyMatch(type -> "FINAL_TRANSCRIPT".equals(type) || "TURN_RESPONSE".equals(type)));
    }

    @Test
    void routesTheFinalAzureResultToTheCommonTurnServiceAfterStop() throws Exception {
        WebSocketSession session = webSocketSession("websocket-1");
        ArgumentCaptor<AzureSpeechRecognitionListener> listener =
                ArgumentCaptor.forClass(AzureSpeechRecognitionListener.class);
        when(voiceSessionService.get(USER_ID, SESSION_ID)).thenReturn(session(VoiceSessionStatus.LISTENING));
        when(azureSpeechClient.open(listener.capture())).thenReturn(stream);

        handler.handleMessage(session, start(FIRST_TURN_ID));
        handler.handleMessage(session, stop(FIRST_TURN_ID));
        listener.getValue().onFinalResult(new AzureSpeechDetailedResult(
                "김철수에게 오만 원 보내줘", new BigDecimal("0.95"), List.of()));

        verify(stream).stop();
        verify(voiceTurnService)
                .processAzureTransferFinal(
                        USER_ID,
                        SESSION_ID,
                        FIRST_TURN_ID,
                        0L,
                        new AzureSpeechDetailedResult(
                                "김철수에게 오만 원 보내줘", new BigDecimal("0.95"), List.of()));
    }

    @Test
    void stopsAzureStreamOnlyOnceWhenFinalResultFollowsStop() throws Exception {
        WebSocketSession session = webSocketSession("websocket-1");
        ArgumentCaptor<AzureSpeechRecognitionListener> listener =
                ArgumentCaptor.forClass(AzureSpeechRecognitionListener.class);
        when(voiceSessionService.get(USER_ID, SESSION_ID)).thenReturn(session(VoiceSessionStatus.LISTENING));
        when(azureSpeechClient.open(listener.capture())).thenReturn(stream);

        handler.handleMessage(session, start(FIRST_TURN_ID));
        handler.handleMessage(session, stop(FIRST_TURN_ID));
        listener.getValue().onFinalResult(new AzureSpeechDetailedResult(
                "김철수에게 오만 원 보내줘", new BigDecimal("0.95"), List.of()));

        verify(stream, timeout(1_000)).close();
        verify(stream, times(1)).stop();
    }

    @Test
    void closesAndReleasesTheActiveStreamWhenStopRecognitionFails() throws Exception {
        WebSocketSession session = webSocketSession("websocket-1");
        when(voiceSessionService.get(USER_ID, SESSION_ID)).thenReturn(session(VoiceSessionStatus.LISTENING));
        when(azureSpeechClient.open(any())).thenReturn(stream);
        doThrow(new BusinessException(ErrorCode.SPEECH_RECOGNITION_FAILED)).when(stream).stop();

        handler.handleMessage(session, start(FIRST_TURN_ID));
        handler.handleMessage(session, stop(FIRST_TURN_ID));

        verify(stream, timeout(1_000)).close();

        handler.handleMessage(session, start(SECOND_TURN_ID));

        verify(azureSpeechClient, times(2)).open(any());
    }

    @Test
    void rejectsAudioAndResumeAfterStopHasBeenRequested() throws Exception {
        WebSocketSession firstSession = webSocketSession("websocket-1");
        WebSocketSession resumedSession = webSocketSession("websocket-2");
        when(voiceSessionService.get(USER_ID, SESSION_ID)).thenReturn(session(VoiceSessionStatus.LISTENING));
        when(azureSpeechClient.open(any())).thenReturn(stream);

        handler.handleMessage(firstSession, start(FIRST_TURN_ID));
        handler.handleMessage(firstSession, stop(FIRST_TURN_ID));
        handler.handleMessage(firstSession, audio(0, new byte[] {1}));
        handler.afterConnectionClosed(firstSession, CloseStatus.NORMAL);
        handler.handleMessage(resumedSession, resume(FIRST_TURN_ID, -1));

        verify(stream, atLeastOnce()).stop();
        verify(stream, never()).write(any());
        verify(firstSession, atLeastOnce()).sendMessage(any(TextMessage.class));
        verify(resumedSession).sendMessage(any(TextMessage.class));
    }

    @Test
    void resumesTheDetachedTransferStreamFromTheNextAudioSequence() throws Exception {
        WebSocketSession firstSession = webSocketSession("websocket-1");
        WebSocketSession resumedSession = webSocketSession("websocket-2");
        ArgumentCaptor<AzureSpeechRecognitionListener> listener =
                ArgumentCaptor.forClass(AzureSpeechRecognitionListener.class);
        when(voiceSessionService.get(USER_ID, SESSION_ID)).thenReturn(session(VoiceSessionStatus.LISTENING));
        when(azureSpeechClient.open(listener.capture())).thenReturn(stream);

        handler.handleMessage(firstSession, start(FIRST_TURN_ID));
        handler.handleMessage(firstSession, audio(0, new byte[] {1}));
        handler.afterConnectionClosed(firstSession, CloseStatus.NORMAL);
        handler.afterConnectionEstablished(resumedSession);
        handler.handleMessage(resumedSession, resume(FIRST_TURN_ID, 0));
        handler.handleMessage(resumedSession, audio(1, new byte[] {2}));
        listener.getValue().onPartialTranscript("김철수에게");

        verify(stream).write(new byte[] {1});
        verify(stream).write(new byte[] {2});
        verify(resumedSession).sendMessage(any(TextMessage.class));

        handler.handleMessage(resumedSession, bargeIn(FIRST_TURN_ID));
    }

    @Test
    void keepsTheResumedStreamWhenThePreviousConnectionCloseRunsConcurrently() throws Exception {
        WebSocketSession firstSession = webSocketSession("websocket-1");
        WebSocketSession resumedSession = webSocketSession("websocket-2");
        when(voiceSessionService.get(USER_ID, SESSION_ID)).thenReturn(session(VoiceSessionStatus.LISTENING));
        when(azureSpeechClient.open(any())).thenReturn(stream);

        handler.handleMessage(firstSession, start(FIRST_TURN_ID));
        handler.afterConnectionClosed(firstSession, CloseStatus.NORMAL);
        handler.handleMessage(resumedSession, resume(FIRST_TURN_ID, -1));

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> lateClose = executor.submit(() -> {
                await(start);
                handler.afterConnectionClosed(firstSession, CloseStatus.NORMAL);
            });
            Future<?> nextAudio = executor.submit(() -> {
                await(start);
                try {
                    handler.handleMessage(resumedSession, audio(0, new byte[] {1}));
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            });

            start.countDown();
            lateClose.get(1, TimeUnit.SECONDS);
            nextAudio.get(1, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        verify(stream).write(new byte[] {1});
        verify(stream, never()).close();
    }

    @Test
    void rejectsResumeWhenTheAuthenticatedUserDoesNotOwnTheDetachedStream() throws Exception {
        WebSocketSession firstSession = webSocketSession("websocket-1");
        WebSocketSession otherUsersSession = webSocketSession(
                "websocket-2", "00000000-0000-0000-0000-000000000099");
        when(voiceSessionService.get(ArgumentMatchers.anyString(), ArgumentMatchers.eq(SESSION_ID)))
                .thenReturn(session(VoiceSessionStatus.LISTENING));
        when(azureSpeechClient.open(any())).thenReturn(stream);

        handler.handleMessage(firstSession, start(FIRST_TURN_ID));
        handler.afterConnectionClosed(firstSession, CloseStatus.NORMAL);
        handler.handleMessage(otherUsersSession, resume(FIRST_TURN_ID, -1));

        verify(otherUsersSession).sendMessage(any(TextMessage.class));
        verify(stream, never()).write(any());
    }

    @Test
    void cancelsTheDetachedStreamWhenItsOwnerResumesWithTheWrongSequence() throws Exception {
        WebSocketSession firstSession = webSocketSession("websocket-1");
        WebSocketSession resumedSession = webSocketSession("websocket-2");
        when(voiceSessionService.get(USER_ID, SESSION_ID)).thenReturn(session(VoiceSessionStatus.LISTENING));
        when(azureSpeechClient.open(any())).thenReturn(stream);

        handler.handleMessage(firstSession, start(FIRST_TURN_ID));
        handler.handleMessage(firstSession, audio(0, new byte[] {1}));
        handler.afterConnectionClosed(firstSession, CloseStatus.NORMAL);
        handler.handleMessage(resumedSession, resume(FIRST_TURN_ID, -1));

        verify(resumedSession).sendMessage(any(TextMessage.class));
        verify(stream, timeout(1_000)).stop();
        verify(stream, timeout(1_000)).close();
    }

    @Test
    void closesTheDetachedStreamWhenTheResumeGracePeriodExpires() throws Exception {
        WebSocketSession session = webSocketSession("websocket-1");
        handler = newHandler(10);
        when(voiceSessionService.get(USER_ID, SESSION_ID)).thenReturn(session(VoiceSessionStatus.LISTENING));
        when(azureSpeechClient.open(any())).thenReturn(stream);

        handler.handleMessage(session, start(FIRST_TURN_ID));
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(stream, timeout(1_000)).stop();
        verify(stream, timeout(1_000)).close();
    }

    private VoiceStreamWebSocketHandler newHandler(long resumeGraceMillis) {
        return new VoiceStreamWebSocketHandler(
                voiceSessionService,
                voiceStreamLifecycleService,
                voiceTurnService,
                azureSpeechClient,
                new ObjectMapper(),
                resumeGraceMillis);
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(exception);
        }
    }

    private TextMessage start(String turnId) {
        return new TextMessage("{\"type\":\"START\",\"turnId\":\"" + turnId + "\"}");
    }

    private TextMessage bargeIn(String turnId) {
        return new TextMessage("{\"type\":\"BARGE_IN\",\"target\":\"INPUT_STREAM\",\"inputTurnId\":\""
                + turnId + "\"}");
    }

    private TextMessage stop(String turnId) {
        return new TextMessage("{\"type\":\"STOP\",\"turnId\":\"" + turnId + "\"}");
    }

    private TextMessage resume(String turnId, long lastReceivedSequence) {
        return new TextMessage("{\"type\":\"RESUME\",\"turnId\":\""
                + turnId
                + "\",\"lastReceivedSequence\":"
                + lastReceivedSequence
                + "}");
    }

    private BinaryMessage audio(long sequence, byte[] pcm) {
        ByteBuffer frame = ByteBuffer.allocate(Integer.BYTES + pcm.length);
        frame.putInt((int) sequence);
        frame.put(pcm);
        return new BinaryMessage(frame.array());
    }

    private void assertInvalidRequestErrorWithRequestId(WebSocketSession session) throws Exception {
        ArgumentCaptor<TextMessage> error = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, atLeastOnce()).sendMessage(error.capture());

        com.fasterxml.jackson.databind.JsonNode payload = error.getAllValues().stream()
                .map(message -> readJson(message.getPayload()))
                .filter(message -> "ERROR".equals(message.path("type").asText()))
                .findFirst()
                .orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("ERROR", payload.path("type").asText());
        org.junit.jupiter.api.Assertions.assertEquals("INVALID_REQUEST", payload.path("code").asText());
        org.junit.jupiter.api.Assertions.assertTrue(payload.has("retryable"));
        org.junit.jupiter.api.Assertions.assertFalse(payload.path("retryable").asBoolean());
        org.junit.jupiter.api.Assertions.assertTrue(
                payload.path("requestId").asText().matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
    }

    private com.fasterxml.jackson.databind.JsonNode readJson(String payload) {
        try {
            return new ObjectMapper().readTree(payload);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private WebSocketSession webSocketSession(String websocketId) {
        return webSocketSession(websocketId, USER_ID);
    }

    private WebSocketSession webSocketSession(String websocketId, String userId) {
        WebSocketSession session = org.mockito.Mockito.mock(WebSocketSession.class);
        Principal principal = () -> userId;
        when(session.getId()).thenReturn(websocketId);
        when(session.getPrincipal()).thenReturn(principal);
        when(session.getUri()).thenReturn(URI.create("ws://localhost/api/voice/sessions/" + SESSION_ID + "/stream"));
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    private VoiceSessionDetailResponse session(VoiceSessionStatus status) {
        return new VoiceSessionDetailResponse(
                SESSION_ID,
                VoiceSessionEntryPoint.TRANSFER,
                status,
                DialogueStep.AWAITING_INPUT,
                VoiceFlowType.TRANSFER,
                SttMode.BACKEND_STREAM,
                null,
                null,
                null,
                null,
                null);
    }
}
