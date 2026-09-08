package com.silvertown.domain.voice.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvertown.domain.voice.dto.VoiceSessionEventRequest;
import com.silvertown.domain.voice.dto.VoiceSessionEventResponse;
import com.silvertown.domain.voice.enums.DialogueStep;
import com.silvertown.domain.voice.enums.SttMode;
import com.silvertown.domain.voice.enums.VoiceFlowType;
import com.silvertown.domain.voice.enums.VoiceSessionStatus;
import com.silvertown.domain.voice.mapper.DialogueTurnMapper;
import com.silvertown.domain.voice.mapper.VoiceSessionMapper;
import com.silvertown.domain.voice.service.VoiceProgressPromptFactory;
import com.silvertown.domain.voice.vo.DialogueTurnVo;
import com.silvertown.domain.voice.vo.VoiceSessionVo;
import com.silvertown.global.common.exception.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class VoiceSessionEventServiceImplTest {
    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String SESSION_ID = "10000000-0000-0000-0000-000000000001";
    private static final String TURN_ID = "20000000-0000-0000-0000-000000000001";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-04T00:00:00Z"), ZoneOffset.UTC);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private VoiceSessionMapper voiceSessionMapper;
    private DialogueTurnMapper dialogueTurnMapper;
    private VoiceSessionEventServiceImpl service;

    @BeforeEach
    void setUp() {
        voiceSessionMapper = org.mockito.Mockito.mock(VoiceSessionMapper.class);
        dialogueTurnMapper = org.mockito.Mockito.mock(DialogueTurnMapper.class);
        service = new VoiceSessionEventServiceImpl(
                voiceSessionMapper,
                dialogueTurnMapper,
                new VoiceProgressPromptFactory(objectMapper),
                CLOCK);
    }

    @Test
    void replaysTheOriginalStoredPayloadWithoutRerunningFinancialLogic() throws Exception {
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(activeSession());
        when(dialogueTurnMapper.findBySessionIdAndTurnId(SESSION_ID, TURN_ID)).thenReturn(aiTurn());
        when(dialogueTurnMapper.incrementReplayCount(TURN_ID)).thenReturn(1);

        VoiceSessionEventResponse response = service.handle(USER_ID, SESSION_ID, request("REPLAY"));

        assertEquals(DialogueStep.AWAITING_AMOUNT, response.getState());
        assertNull(response.getInvalidatedTurnId());
        assertEquals("김영희 님에게 송금할 금액을 말씀해 주세요.", response.getReplayPayload().getTtsText());
        assertEquals("<speak>저장된 SSML</speak>", response.getReplayPayload().getTtsSsml());
        assertEquals("김영희", response.getReplayPayload().getDisplayCard().get("recipient").asText());
        verify(dialogueTurnMapper).incrementReplayCount(TURN_ID);
    }

    @Test
    void replaysTheFollowingAiTurnWhenTheClientSuppliesItsUserTurnId() throws Exception {
        DialogueTurnVo userTurn = new DialogueTurnVo();
        userTurn.setTurnId(TURN_ID);
        userTurn.setSessionId(SESSION_ID);
        userTurn.setSequenceNo(3);
        userTurn.setSpeaker("USER");
        DialogueTurnVo savedAiTurn = aiTurn();
        savedAiTurn.setTurnId("30000000-0000-0000-0000-000000000001");
        savedAiTurn.setSequenceNo(4);
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(activeSession());
        when(dialogueTurnMapper.findBySessionIdAndTurnId(SESSION_ID, TURN_ID)).thenReturn(userTurn);
        when(dialogueTurnMapper.findBySessionIdAndSequenceNo(SESSION_ID, 4)).thenReturn(savedAiTurn);
        when(dialogueTurnMapper.incrementReplayCount(savedAiTurn.getTurnId())).thenReturn(1);

        VoiceSessionEventResponse response = service.handle(USER_ID, SESSION_ID, request("REPLAY"));

        assertEquals("김영희 님에게 송금할 금액을 말씀해 주세요.", response.getReplayPayload().getTtsText());
        assertEquals("<speak>저장된 SSML</speak>", response.getReplayPayload().getTtsSsml());
        verify(dialogueTurnMapper).incrementReplayCount(savedAiTurn.getTurnId());
    }

    @Test
    void invalidatesTheRequestedAiTurnWhenInterrupted() throws Exception {
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(activeSession());
        when(dialogueTurnMapper.findBySessionIdAndTurnId(SESSION_ID, TURN_ID)).thenReturn(aiTurn());
        when(dialogueTurnMapper.markInterrupted(TURN_ID)).thenReturn(1);

        VoiceSessionEventResponse response = service.handle(USER_ID, SESSION_ID, request("INTERRUPTED"));

        assertEquals(TURN_ID, response.getInvalidatedTurnId());
        assertNull(response.getReplayPayload());
        verify(dialogueTurnMapper).markInterrupted(TURN_ID);
        verify(voiceSessionMapper).updateStatusAndStep(
                USER_ID, SESSION_ID, VoiceSessionStatus.LISTENING.name(), DialogueStep.AWAITING_AMOUNT.name());
    }

    @Test
    void rejectsUserTurnIdForInterruptedWithoutInvalidatingTheFollowingAiTurn() throws Exception {
        DialogueTurnVo userTurn = new DialogueTurnVo();
        userTurn.setTurnId(TURN_ID);
        userTurn.setSessionId(SESSION_ID);
        userTurn.setSequenceNo(3);
        userTurn.setSpeaker("USER");
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(activeSession());
        when(dialogueTurnMapper.findBySessionIdAndTurnId(SESSION_ID, TURN_ID)).thenReturn(userTurn);

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.handle(USER_ID, SESSION_ID, request("INTERRUPTED")));

        assertEquals("VOICE_SESSION_NOT_FOUND", exception.getErrorCode().getCode());
        verify(dialogueTurnMapper, never()).findBySessionIdAndSequenceNo(SESSION_ID, 4);
        verify(dialogueTurnMapper, never()).markInterrupted(any());
    }

    @Test
    void replaysTheBusinessProgressWhenTheTargetIsAContinuationQuestion() throws Exception {
        VoiceSessionVo session = activeSession();
        session.setCurrentStep(DialogueStep.AWAITING_CONTINUATION.name());
        DialogueTurnVo continuation = aiTurn();
        continuation.setStep(DialogueStep.AWAITING_CONTINUATION.name());
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(session);
        when(dialogueTurnMapper.findBySessionIdAndTurnId(SESSION_ID, TURN_ID)).thenReturn(continuation);
        when(dialogueTurnMapper.incrementReplayCount(TURN_ID)).thenReturn(1);
        when(dialogueTurnMapper.findLatestBusinessAiTurn(SESSION_ID)).thenReturn(aiTurn());

        VoiceSessionEventResponse response = service.handle(USER_ID, SESSION_ID, request("REPLAY"));

        assertEquals(DialogueStep.AWAITING_CONTINUATION, response.getState());
        assertEquals("김영희 님에게 송금할 금액을 말씀해 주세요.", response.getReplayPayload().getTtsText());
        assertEquals("<speak>저장된 SSML</speak>", response.getReplayPayload().getTtsSsml());
        assertEquals("김영희", response.getReplayPayload().getDisplayCard().get("recipient").asText());
        verify(dialogueTurnMapper).findLatestBusinessAiTurn(SESSION_ID);
    }

    @Test
    void rejectsTransferSessionsFromTheHttpEventEndpoint() throws Exception {
        VoiceSessionVo transferSession = activeSession();
        transferSession.setFlowType(VoiceFlowType.TRANSFER.name());
        transferSession.setSttMode(SttMode.BACKEND_STREAM.name());
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(transferSession);

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.handle(USER_ID, SESSION_ID, request("REPLAY")));

        assertEquals("INVALID_REQUEST", exception.getErrorCode().getCode());
    }

    @Test
    void hidesMissingOrOtherUsersSessionAsNotFound() throws Exception {
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.handle(USER_ID, SESSION_ID, request("REPLAY")));

        assertEquals("VOICE_SESSION_NOT_FOUND", exception.getErrorCode().getCode());
    }

    @Test
    void blocksReplayAfterTheAiTurnWasInterrupted() throws Exception {
        DialogueTurnVo prompt = aiTurn();
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID))
                .thenReturn(activeSession(), activeSession());
        when(dialogueTurnMapper.findBySessionIdAndTurnId(SESSION_ID, TURN_ID)).thenReturn(prompt);
        when(dialogueTurnMapper.markInterrupted(TURN_ID)).thenAnswer(invocation -> {
            prompt.setInterrupted(true);
            return 1;
        });

        service.handle(USER_ID, SESSION_ID, request("INTERRUPTED"));

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.handle(USER_ID, SESSION_ID, request("REPLAY")));

        assertEquals("VOICE_SESSION_NOT_FOUND", exception.getErrorCode().getCode());
        verify(dialogueTurnMapper, never()).incrementReplayCount(TURN_ID);
    }

    @Test
    void rejectsClosedAndExpiredSessions() throws Exception {
        VoiceSessionVo closed = activeSession();
        closed.setStatus(VoiceSessionStatus.CLOSED.name());
        VoiceSessionVo expired = activeSession();
        expired.setExpiresAt(LocalDateTime.ofInstant(CLOCK.instant().minusSeconds(1), ZoneOffset.UTC));
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(closed, expired);

        BusinessException closedException = assertThrows(
                BusinessException.class, () -> service.handle(USER_ID, SESSION_ID, request("REPLAY")));
        BusinessException expiredException = assertThrows(
                BusinessException.class, () -> service.handle(USER_ID, SESSION_ID, request("REPLAY")));

        assertEquals("VOICE_TURN_CONFLICT", closedException.getErrorCode().getCode());
        assertEquals("VOICE_TURN_CONFLICT", expiredException.getErrorCode().getCode());
    }

    private VoiceSessionEventRequest request(String eventType) throws Exception {
        return objectMapper.readValue(
                "{\"eventType\":\"" + eventType + "\",\"turnId\":\"" + TURN_ID + "\"}",
                VoiceSessionEventRequest.class);
    }

    private VoiceSessionVo activeSession() {
        VoiceSessionVo session = new VoiceSessionVo();
        session.setSessionId(SESSION_ID);
        session.setUserId(USER_ID);
        session.setStatus(VoiceSessionStatus.SPEAKING.name());
        session.setCurrentStep(DialogueStep.AWAITING_AMOUNT.name());
        session.setFlowType(VoiceFlowType.GENERAL_FINANCE.name());
        session.setSttMode(SttMode.CLIENT.name());
        session.setExpiresAt(LocalDateTime.ofInstant(CLOCK.instant().plusSeconds(60), ZoneOffset.UTC));
        return session;
    }

    private DialogueTurnVo aiTurn() throws Exception {
        DialogueTurnVo turn = new DialogueTurnVo();
        turn.setTurnId(TURN_ID);
        turn.setSessionId(SESSION_ID);
        turn.setSequenceNo(4);
        turn.setSpeaker("AI");
        turn.setStep(DialogueStep.AWAITING_AMOUNT.name());
        turn.setTtsText("김영희 님에게 송금할 금액을 말씀해 주세요.");
        turn.setTtsSsml("<speak>저장된 SSML</speak>");
        turn.setDisplayCard(objectMapper.writeValueAsString(Map.of("recipient", "김영희")));
        turn.setExtractedSlots(objectMapper.writeValueAsString(Map.of(
                "slots", Map.of("recipient", "김영희"),
                "confidence", 0.95,
                "nextAction", "ASK_AMOUNT")));
        return turn;
    }
}
