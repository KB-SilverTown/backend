package com.silvertown.domain.voice.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvertown.domain.voice.enums.DialogueStep;
import com.silvertown.domain.voice.enums.SttMode;
import com.silvertown.domain.voice.enums.VoiceFlowType;
import com.silvertown.domain.voice.enums.VoiceSessionStatus;
import com.silvertown.domain.voice.mapper.DialogueTurnMapper;
import com.silvertown.domain.voice.mapper.VoiceSessionMapper;
import com.silvertown.domain.voice.mapper.UserVoiceSettingsMapper;
import com.silvertown.domain.voice.vo.DialogueTurnVo;
import com.silvertown.domain.voice.vo.VoiceSessionVo;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class VoiceSilenceTimeoutServiceTest {
    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String SESSION_ID = "10000000-0000-0000-0000-000000000001";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-04T00:00:00Z"), ZoneOffset.UTC);

    private VoiceSessionMapper voiceSessionMapper;
    private DialogueTurnMapper dialogueTurnMapper;
    private VoiceTransferOrchestrator voiceTransferOrchestrator;
    private VoiceSilenceTimeoutService service;

    @BeforeEach
    void setUp() {
        voiceSessionMapper = org.mockito.Mockito.mock(VoiceSessionMapper.class);
        dialogueTurnMapper = org.mockito.Mockito.mock(DialogueTurnMapper.class);
        voiceTransferOrchestrator = org.mockito.Mockito.mock(VoiceTransferOrchestrator.class);
        PlatformTransactionManager transactionManager = org.mockito.Mockito.mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        ObjectMapper objectMapper = new ObjectMapper();
        service = new VoiceSilenceTimeoutService(
                voiceSessionMapper,
                dialogueTurnMapper,
                new VoiceProgressPromptFactory(objectMapper),
                new VoiceSsmlRenderer(org.mockito.Mockito.mock(UserVoiceSettingsMapper.class)),
                voiceTransferOrchestrator,
                objectMapper,
                CLOCK,
                transactionManager);
    }

    @Test
    void storesFifteenSecondsAndCreatesOneContinuationQuestionOnFirstSilence() {
        VoiceSessionVo session = activeSession(DialogueStep.AWAITING_AMOUNT);
        when(voiceSessionMapper.findActiveInteractiveSessions(any())).thenReturn(List.of(session));
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(session);
        when(dialogueTurnMapper.findLatestBySessionIdForUpdate(SESSION_ID)).thenReturn(aiTurn(3));
        when(dialogueTurnMapper.recordSilenceMs(any(), eq(15_000))).thenReturn(1);
        when(dialogueTurnMapper.findNextSequenceNo(SESSION_ID)).thenReturn(4);
        when(voiceSessionMapper.updateStatusAndStep(
                USER_ID,
                SESSION_ID,
                VoiceSessionStatus.SPEAKING.name(),
                DialogueStep.AWAITING_CONTINUATION.name())).thenReturn(1);

        service.handleExpiredSilence();

        verify(dialogueTurnMapper).recordSilenceMs("20000000-0000-0000-0000-000000000003", 15_000);
        ArgumentCaptor<DialogueTurnVo> prompt = ArgumentCaptor.forClass(DialogueTurnVo.class);
        verify(dialogueTurnMapper).insert(prompt.capture());
        org.junit.jupiter.api.Assertions.assertEquals(
                DialogueStep.AWAITING_CONTINUATION.name(), prompt.getValue().getStep());
        verify(voiceSessionMapper).updateStatusAndStep(
                USER_ID,
                SESSION_ID,
                VoiceSessionStatus.SPEAKING.name(),
                DialogueStep.AWAITING_CONTINUATION.name());
    }

    @Test
    void closesTheSessionAfterSilenceDuringTheSingleContinuationQuestion() {
        VoiceSessionVo session = activeSession(DialogueStep.AWAITING_CONTINUATION);
        when(voiceSessionMapper.findActiveInteractiveSessions(any())).thenReturn(List.of(session));
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(session);
        when(dialogueTurnMapper.findLatestBySessionIdForUpdate(SESSION_ID)).thenReturn(aiTurn(5));
        when(dialogueTurnMapper.recordSilenceMs(any(), eq(15_000))).thenReturn(1);
        when(dialogueTurnMapper.findNextSequenceNo(SESSION_ID)).thenReturn(6);
        when(voiceSessionMapper.closeOwned(any(), any(), any(), any())).thenReturn(1);

        service.handleExpiredSilence();

        verify(voiceSessionMapper).closeOwned(
                eq(USER_ID), eq(SESSION_ID), eq(DialogueStep.CANCELLED.name()), any());
        verify(voiceSessionMapper, times(0)).updateStatusAndStep(any(), any(), any(), any());
    }

    @Test
    void leavesBackendStreamSilenceToTheFrontendAfterTtsPlayback() {
        VoiceSessionVo session = activeSession(DialogueStep.AWAITING_CONTINUATION);
        session.setSttMode(SttMode.BACKEND_STREAM.name());
        when(voiceSessionMapper.findActiveInteractiveSessions(any())).thenReturn(List.of(session));
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(session);

        service.handleExpiredSilence();

        verifyNoInteractions(dialogueTurnMapper, voiceTransferOrchestrator);
        verify(voiceSessionMapper, times(0)).closeOwned(any(), any(), any(), any());
    }

    private VoiceSessionVo activeSession(DialogueStep step) {
        VoiceSessionVo session = new VoiceSessionVo();
        session.setSessionId(SESSION_ID);
        session.setUserId(USER_ID);
        session.setStatus(VoiceSessionStatus.SPEAKING.name());
        session.setCurrentStep(step.name());
        session.setFlowType(VoiceFlowType.GENERAL_FINANCE.name());
        session.setSttMode(SttMode.CLIENT.name());
        session.setExpiresAt(LocalDateTime.ofInstant(CLOCK.instant().plusSeconds(60), ZoneOffset.UTC));
        return session;
    }

    private DialogueTurnVo aiTurn(int sequenceNo) {
        DialogueTurnVo turn = new DialogueTurnVo();
        turn.setTurnId("20000000-0000-0000-0000-00000000000" + sequenceNo);
        turn.setSessionId(SESSION_ID);
        turn.setSequenceNo(sequenceNo);
        turn.setSpeaker("AI");
        turn.setCreatedAt(LocalDateTime.ofInstant(CLOCK.instant().minusSeconds(21), ZoneOffset.UTC));
        return turn;
    }
}
