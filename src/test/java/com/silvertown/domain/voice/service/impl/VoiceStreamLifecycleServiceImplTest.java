package com.silvertown.domain.voice.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.silvertown.domain.voice.enums.SttMode;
import com.silvertown.domain.voice.enums.VoiceFlowType;
import com.silvertown.domain.voice.enums.VoiceSessionStatus;
import com.silvertown.domain.voice.mapper.DialogueTurnMapper;
import com.silvertown.domain.voice.mapper.VoiceSessionMapper;
import com.silvertown.domain.voice.service.VoiceStreamLifecycleService;
import com.silvertown.domain.voice.vo.VoiceSessionVo;
import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.CannotAcquireLockException;

class VoiceStreamLifecycleServiceImplTest {
    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String SESSION_ID = "10000000-0000-0000-0000-000000000001";
    private static final String INPUT_TURN_ID = "20000000-0000-0000-0000-000000000001";
    private static final String AI_TURN_ID = "30000000-0000-0000-0000-000000000001";
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-09T01:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    private VoiceSessionMapper voiceSessionMapper;
    private DialogueTurnMapper dialogueTurnMapper;
    private VoiceStreamLifecycleService service;

    @BeforeEach
    void setUp() {
        voiceSessionMapper = Mockito.mock(VoiceSessionMapper.class);
        dialogueTurnMapper = Mockito.mock(DialogueTurnMapper.class);
        service = new VoiceStreamLifecycleServiceImpl(voiceSessionMapper, dialogueTurnMapper, CLOCK);
    }

    @Test
    void claimsOnlyListeningBackendTransferSessionWithoutActiveTurns() {
        VoiceSessionVo session = session(VoiceSessionStatus.LISTENING, null, null, 4);
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(session);
        when(voiceSessionMapper.claimStreamInputTurn(USER_ID, SESSION_ID, INPUT_TURN_ID, NOW)).thenReturn(1);

        long generation = service.claimInputTurn(USER_ID, SESSION_ID, INPUT_TURN_ID);

        assertEquals(4, generation);
        verify(voiceSessionMapper).claimStreamInputTurn(USER_ID, SESSION_ID, INPUT_TURN_ID, NOW);
    }

    @Test
    void rejectsInputClaimWhenAnotherTurnIsAlreadyActive() {
        VoiceSessionVo session = session(VoiceSessionStatus.LISTENING, INPUT_TURN_ID, null, 0);
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(session);

        assertTurnConflict(() -> service.claimInputTurn(USER_ID, SESSION_ID, INPUT_TURN_ID));

        verify(voiceSessionMapper, never()).claimStreamInputTurn(USER_ID, SESSION_ID, INPUT_TURN_ID, NOW);
    }

    @Test
    void beginsFinalProcessingOnlyForMatchingInputAndGeneration() {
        VoiceSessionVo session = session(VoiceSessionStatus.LISTENING, INPUT_TURN_ID, null, 2);
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(session);
        when(voiceSessionMapper.beginStreamFinalProcessing(USER_ID, SESSION_ID, INPUT_TURN_ID, 2, NOW))
                .thenReturn(1);

        service.beginFinalProcessing(USER_ID, SESSION_ID, INPUT_TURN_ID, 2);

        verify(voiceSessionMapper).beginStreamFinalProcessing(USER_ID, SESSION_ID, INPUT_TURN_ID, 2, NOW);
    }

    @Test
    void rejectsFinalProcessingForStaleGeneration() {
        VoiceSessionVo session = session(VoiceSessionStatus.LISTENING, INPUT_TURN_ID, null, 2);
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(session);

        assertTurnConflict(() -> service.beginFinalProcessing(USER_ID, SESSION_ID, INPUT_TURN_ID, 1));

        verify(voiceSessionMapper, never()).beginStreamFinalProcessing(
                eq(USER_ID), eq(SESSION_ID), eq(INPUT_TURN_ID), Mockito.anyLong(), eq(NOW));
    }

    @Test
    void completesProcessingByReplacingActiveInputWithAiTurn() {
        VoiceSessionVo session = session(VoiceSessionStatus.PROCESSING, INPUT_TURN_ID, null, 3);
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(session);
        when(voiceSessionMapper.completeStreamTurnWithAi(USER_ID, SESSION_ID, INPUT_TURN_ID, 3, AI_TURN_ID, NOW))
                .thenReturn(1);

        service.completeAiTurn(USER_ID, SESSION_ID, INPUT_TURN_ID, 3, AI_TURN_ID);

        verify(voiceSessionMapper).completeStreamTurnWithAi(
                USER_ID, SESSION_ID, INPUT_TURN_ID, 3, AI_TURN_ID, NOW);
    }

    @Test
    void interruptsOnlyTheCurrentAiTurn() {
        VoiceSessionVo session = session(VoiceSessionStatus.SPEAKING, null, AI_TURN_ID, 3);
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(session);
        when(dialogueTurnMapper.markInterrupted(AI_TURN_ID)).thenReturn(1);
        when(voiceSessionMapper.interruptActiveAiTurn(USER_ID, SESSION_ID, AI_TURN_ID, 3, NOW)).thenReturn(1);

        service.interruptAiTts(USER_ID, SESSION_ID, AI_TURN_ID, 3);

        verify(dialogueTurnMapper).markInterrupted(AI_TURN_ID);
        verify(voiceSessionMapper).interruptActiveAiTurn(USER_ID, SESSION_ID, AI_TURN_ID, 3, NOW);
    }

    @Test
    void rejectsMismatchedAiTurnAndExpiredSession() {
        VoiceSessionVo speaking = session(VoiceSessionStatus.SPEAKING, null, AI_TURN_ID, 3);
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(speaking);

        assertTurnConflict(() -> service.interruptAiTts(
                USER_ID, SESSION_ID, "30000000-0000-0000-0000-000000000002", 3));
        verify(dialogueTurnMapper, never()).markInterrupted(Mockito.anyString());

        VoiceSessionVo expired = session(VoiceSessionStatus.EXPIRED, null, null, 3);
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(expired);
        assertTurnConflict(() -> service.claimInputTurn(USER_ID, SESSION_ID, INPUT_TURN_ID));
    }

    @Test
    void rejectsMissingOrNonCanonicalTurnIdBeforeAcquiringTheSessionLock() {
        BusinessException missing = assertThrows(
                BusinessException.class, () -> service.claimInputTurn(USER_ID, SESSION_ID, null));
        BusinessException malformed = assertThrows(
                BusinessException.class, () -> service.claimInputTurn(USER_ID, SESSION_ID, "input-turn"));

        assertEquals(ErrorCode.INVALID_REQUEST, missing.getErrorCode());
        assertEquals(ErrorCode.INVALID_REQUEST, malformed.getErrorCode());
        verify(voiceSessionMapper, never()).findOwnedByIdForUpdate(USER_ID, SESSION_ID);
    }

    @Test
    void rejectsStaleInputCancellationEvenWhenTheTurnIdWasReused() {
        VoiceSessionVo newerStream = session(VoiceSessionStatus.LISTENING, INPUT_TURN_ID, null, 6);
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(newerStream);

        assertTurnConflict(() -> service.cancelInputStream(USER_ID, SESSION_ID, INPUT_TURN_ID, 5));

        verify(voiceSessionMapper, never()).cancelActiveInputTurn(
                eq(USER_ID), eq(SESSION_ID), eq(INPUT_TURN_ID), eq(5L), eq(NOW));
    }

    @Test
    void rejectsStaleAiInterruptionEvenWhenTheTurnIdWasReused() {
        VoiceSessionVo newerTurn = session(VoiceSessionStatus.SPEAKING, null, AI_TURN_ID, 6);
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(newerTurn);

        assertTurnConflict(() -> service.interruptAiTts(USER_ID, SESSION_ID, AI_TURN_ID, 5));

        verify(dialogueTurnMapper, never()).markInterrupted(AI_TURN_ID);
        verify(voiceSessionMapper, never()).interruptActiveAiTurn(
                eq(USER_ID), eq(SESSION_ID), eq(AI_TURN_ID), eq(5L), eq(NOW));
    }

    @Test
    void cancelsCurrentInputStreamAndRejectsClosedSessions() {
        VoiceSessionVo active = session(VoiceSessionStatus.PROCESSING, INPUT_TURN_ID, null, 5);
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(active);
        when(voiceSessionMapper.cancelActiveInputTurn(USER_ID, SESSION_ID, INPUT_TURN_ID, 5, NOW)).thenReturn(1);

        service.cancelInputStream(USER_ID, SESSION_ID, INPUT_TURN_ID, 5);

        VoiceSessionVo closed = session(VoiceSessionStatus.CLOSED, null, null, 5);
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(closed);
        assertTurnConflict(() -> service.claimInputTurn(USER_ID, SESSION_ID, INPUT_TURN_ID));
        verify(voiceSessionMapper).cancelActiveInputTurn(USER_ID, SESSION_ID, INPUT_TURN_ID, 5, NOW);
    }

    @Test
    void mapsNoWaitLockFailureToVoiceTurnConflict() {
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenThrow(
                new CannotAcquireLockException("voice session is already locked"));

        assertTurnConflict(() -> service.claimInputTurn(USER_ID, SESSION_ID, INPUT_TURN_ID));
    }

    private VoiceSessionVo session(
            VoiceSessionStatus status, String activeInputTurnId, String activeAiTurnId, long generation) {
        VoiceSessionVo session = new VoiceSessionVo();
        session.setStatus(status.name());
        session.setFlowType(VoiceFlowType.TRANSFER.name());
        session.setSttMode(SttMode.BACKEND_STREAM.name());
        session.setActiveInputTurnId(activeInputTurnId);
        session.setActiveAiTurnId(activeAiTurnId);
        session.setLifecycleGeneration(generation);
        session.setExpiresAt(NOW.plusMinutes(15));
        return session;
    }

    private void assertTurnConflict(org.junit.jupiter.api.function.Executable executable) {
        BusinessException exception = assertThrows(BusinessException.class, executable);
        assertEquals(ErrorCode.VOICE_TURN_CONFLICT, exception.getErrorCode());
    }
}
