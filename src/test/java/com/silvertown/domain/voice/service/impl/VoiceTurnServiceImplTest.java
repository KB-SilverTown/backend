package com.silvertown.domain.voice.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvertown.domain.recipient.dto.RecipientCandidateResponse;
import com.silvertown.domain.transfer.dto.AmountValidationResponse;
import com.silvertown.domain.transfer.dto.TransferConfirmRequest;
import com.silvertown.domain.transfer.dto.TransferPrepareResponse;
import com.silvertown.domain.transfer.dto.TransferRecipientResponse;
import com.silvertown.domain.transfer.dto.TransferResponse;
import com.silvertown.domain.risk.dto.RiskScoreResponse;
import com.silvertown.domain.transfer.service.TransferService;
import com.silvertown.domain.risk.service.RiskScoreService;
import com.silvertown.domain.recipient.service.RecipientService;
import com.silvertown.domain.voice.amount.AmountCandidateDecision;
import com.silvertown.domain.voice.amount.AmountCandidateDecisionType;
import com.silvertown.domain.voice.amount.KoreanAmountCandidateGenerator;
import com.silvertown.domain.voice.dto.VoiceTurnRequest;
import com.silvertown.domain.voice.dto.VoiceTurnResponse;
import com.silvertown.domain.voice.enums.DialogueInputType;
import com.silvertown.domain.voice.enums.DialogueStep;
import com.silvertown.domain.voice.enums.VoiceFlowType;
import com.silvertown.domain.voice.enums.VoiceIntent;
import com.silvertown.domain.voice.enums.VoiceNextAction;
import com.silvertown.domain.voice.enums.VoiceRequestedFunction;
import com.silvertown.domain.voice.enums.VoiceSessionStatus;
import com.silvertown.domain.voice.mapper.DialogueTurnMapper;
import com.silvertown.domain.voice.mapper.VoiceInteractionCardMapper;
import com.silvertown.domain.voice.mapper.VoiceSessionMapper;
import com.silvertown.domain.voice.service.AccountVoiceResponseResolver;
import com.silvertown.domain.voice.service.BillVoiceResponseResolver;
import com.silvertown.domain.voice.service.MobileBranchVoiceResponseResolver;
import com.silvertown.domain.voice.service.VoiceProgressPromptFactory;
import com.silvertown.domain.voice.service.VoiceInteractionCardIssuer;
import com.silvertown.domain.voice.service.VoiceSsmlRenderer;
import com.silvertown.domain.voice.service.VoiceTransferOrchestrator;
import com.silvertown.domain.voice.service.VoiceTurnAnalysisPort;
import com.silvertown.domain.voice.service.VoiceTurnAnalysisResult;
import com.silvertown.domain.voice.stt.AzureSpeechDetailedResult;
import com.silvertown.domain.voice.vo.DialogueTurnVo;
import com.silvertown.domain.voice.vo.VoiceInteractionCardVo;
import com.silvertown.domain.voice.vo.VoiceSessionVo;
import com.silvertown.global.common.exception.BusinessException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.ibatis.exceptions.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class VoiceTurnServiceImplTest {
    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String SESSION_ID = "10000000-0000-0000-0000-000000000001";
    private static final String TURN_ID = "20000000-0000-0000-0000-000000000001";
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-03T01:00:00Z"), ZoneOffset.UTC);

    private VoiceSessionMapper voiceSessionMapper;
    private DialogueTurnMapper dialogueTurnMapper;
    private VoiceTurnAnalysisPort voiceTurnAnalysisPort;
    private KoreanAmountCandidateGenerator amountCandidateGenerator;
    private TransferService transferService;
    private RiskScoreService riskScoreService;
    private RecipientService recipientService;
    private VoiceInteractionCardIssuer voiceInteractionCardIssuer;
    private VoiceInteractionCardMapper voiceInteractionCardMapper;
    private AccountVoiceResponseResolver accountVoiceResponseResolver;
    private BillVoiceResponseResolver billVoiceResponseResolver;
    private MobileBranchVoiceResponseResolver mobileBranchVoiceResponseResolver;
    private VoiceSsmlRenderer voiceSsmlRenderer;
    private PlatformTransactionManager transactionManager;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private VoiceTurnServiceImpl service;

    @BeforeEach
    void setUp() {
        voiceSessionMapper = org.mockito.Mockito.mock(VoiceSessionMapper.class);
        dialogueTurnMapper = org.mockito.Mockito.mock(DialogueTurnMapper.class);
        voiceTurnAnalysisPort = org.mockito.Mockito.mock(VoiceTurnAnalysisPort.class);
        amountCandidateGenerator = org.mockito.Mockito.mock(KoreanAmountCandidateGenerator.class);
        transferService = org.mockito.Mockito.mock(TransferService.class);
        riskScoreService = org.mockito.Mockito.mock(RiskScoreService.class);
        recipientService = org.mockito.Mockito.mock(RecipientService.class);
        voiceInteractionCardIssuer = org.mockito.Mockito.mock(VoiceInteractionCardIssuer.class);
        voiceInteractionCardMapper = org.mockito.Mockito.mock(VoiceInteractionCardMapper.class);
        when(voiceInteractionCardIssuer.issueIfInteractive(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        accountVoiceResponseResolver = org.mockito.Mockito.mock(AccountVoiceResponseResolver.class);
        billVoiceResponseResolver = org.mockito.Mockito.mock(BillVoiceResponseResolver.class);
        when(accountVoiceResponseResolver.resolve(any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        voiceSsmlRenderer = org.mockito.Mockito.mock(VoiceSsmlRenderer.class);
        when(billVoiceResponseResolver.resolve(any(), any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(3));
        mobileBranchVoiceResponseResolver = org.mockito.Mockito.mock(MobileBranchVoiceResponseResolver.class);
        when(mobileBranchVoiceResponseResolver.resolve(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(voiceSsmlRenderer.render(eq(USER_ID), any(), any())).thenAnswer(
                invocation -> "<saved-ssml>" + invocation.getArgument(1) + "</saved-ssml>");
        transactionManager = org.mockito.Mockito.mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(
                new SimpleTransactionStatus());
        service = new VoiceTurnServiceImpl(
                voiceSessionMapper, dialogueTurnMapper, voiceTurnAnalysisPort,
                new VoiceProgressPromptFactory(objectMapper), voiceSsmlRenderer,
                amountCandidateGenerator, testOrchestrator(),
                voiceInteractionCardIssuer, voiceInteractionCardMapper,
                accountVoiceResponseResolver,
                billVoiceResponseResolver,
                mobileBranchVoiceResponseResolver,
                objectMapper, CLOCK,
                transactionManager);
    }

    @Test
    void savesUserAndAiTurnsAndReturnsTheDocumentedResponseWithoutPromptType() throws Exception {
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID))
                .thenReturn(activeSession(), processingSession());
        when(voiceSessionMapper.claimForTurn(eq(USER_ID), eq(SESSION_ID), any())).thenReturn(1);
        when(dialogueTurnMapper.findNextSequenceNo(SESSION_ID)).thenReturn(2, 3);
        when(voiceTurnAnalysisPort.analyze(any())).thenReturn(analysis());
        when(voiceSessionMapper.completeTurn(USER_ID, SESSION_ID, DialogueStep.AWAITING_AMOUNT.name()))
                .thenReturn(1);

        VoiceTurnResponse response = service.process(USER_ID, SESSION_ID, request());

        assertEquals(DialogueStep.AWAITING_AMOUNT, response.getState());
        assertEquals(SESSION_ID, response.getSessionId());
        assertEquals(TURN_ID, response.getTurnId());
        assertEquals("TRANSFER", response.getIntent());
        assertEquals("TRANSFER_RECIPIENT_CANDIDATES", response.getRequestedFunction());
        assertEquals("ASK_AMOUNT", response.getNextAction());
        assertTrue(response.getAiTurnId() != null && !response.getAiTurnId().isBlank());
        assertFalse(objectMapper.valueToTree(response).has("promptType"));

        ArgumentCaptor<DialogueTurnVo> turns = ArgumentCaptor.forClass(DialogueTurnVo.class);
        verify(dialogueTurnMapper, times(2)).insert(turns.capture());
        List<DialogueTurnVo> savedTurns = turns.getAllValues();
        assertEquals("USER", savedTurns.get(0).getSpeaker());
        assertEquals(TURN_ID, savedTurns.get(0).getTurnId());
        assertEquals(2, savedTurns.get(0).getSequenceNo());
        assertEquals("AI", savedTurns.get(1).getSpeaker());
        assertEquals(3, savedTurns.get(1).getSequenceNo());
        assertEquals("<saved-ssml>김철수 님에게 보낼 금액을 말씀해 주세요.</saved-ssml>",
                savedTurns.get(1).getTtsSsml());
        assertEquals(savedTurns.get(1).getTtsSsml(), response.getTtsSsml());

        JsonNode stored = objectMapper.readTree(savedTurns.get(1).getExtractedSlots());
        assertEquals("ASK_AMOUNT", stored.get("nextAction").asText());
        assertEquals("TRANSFER_RECIPIENT_CANDIDATES", stored.get("requestedFunction").asText());
        assertEquals("김철수", stored.get("slots").get("recipient").asText());
        verify(voiceSessionMapper).completeTurn(USER_ID, SESSION_ID, DialogueStep.AWAITING_AMOUNT.name());
        verify(voiceInteractionCardMapper).deactivateActiveBySessionId(SESSION_ID);
    }

    @Test
    void rejectsCancelledAzureFinalBeforeItCanCreateTurnOrTransferSideEffects() {
        VoiceSessionVo cancelled = processingBackendTransferSession();
        cancelled.setActiveInputTurnId("20000000-0000-0000-0000-000000000099");
        cancelled.setLifecycleGeneration(8);
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(cancelled);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.processAzureTransferFinal(
                USER_ID,
                SESSION_ID,
                TURN_ID,
                7,
                new AzureSpeechDetailedResult("김철수에게 오만 원 보내줘", new BigDecimal("0.95"), List.of())));

        assertEquals(com.silvertown.global.common.exception.ErrorCode.VOICE_TURN_CONFLICT, exception.getErrorCode());
        verify(voiceTurnAnalysisPort, never()).analyze(any());
        verify(dialogueTurnMapper, never()).insert(any());
        verify(voiceInteractionCardIssuer, never()).issueIfInteractive(any(), any(), any());
    }

    @Test
    void rechecksTheGenerationImmediatelyBeforePersistingAzureFinalSideEffects() {
        VoiceSessionVo claimed = processingBackendTransferSession();
        claimed.setActiveInputTurnId(TURN_ID);
        claimed.setLifecycleGeneration(7);
        VoiceSessionVo cancelledAfterAnalysis = processingBackendTransferSession();
        cancelledAfterAnalysis.setLifecycleGeneration(8);
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID))
                .thenReturn(claimed, cancelledAfterAnalysis, cancelledAfterAnalysis);
        when(amountCandidateGenerator.decide(any())).thenReturn(new AmountCandidateDecision(
                AmountCandidateDecisionType.REASK, null, List.of(), false));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.processAzureTransferFinal(
                USER_ID,
                SESSION_ID,
                TURN_ID,
                7,
                new AzureSpeechDetailedResult("김철수에게 오만 원 보내줘", new BigDecimal("0.95"), List.of())));

        assertEquals(com.silvertown.global.common.exception.ErrorCode.VOICE_TURN_CONFLICT, exception.getErrorCode());
        verify(voiceTurnAnalysisPort, never()).analyze(any());
        verify(dialogueTurnMapper, never()).insert(any());
        verify(voiceInteractionCardIssuer, never()).issueIfInteractive(any(), any(), any());
    }

    @Test
    void doesNotEnrichRecipientCandidatesAfterTheAmountStepHasStarted() throws Exception {
        VoiceSessionVo listening = activeSession();
        listening.setFlowType(VoiceFlowType.TRANSFER.name());
        listening.setCurrentStep(DialogueStep.AWAITING_AMOUNT.name());
        VoiceSessionVo processing = processingSession();
        processing.setFlowType(VoiceFlowType.TRANSFER.name());
        processing.setCurrentStep(DialogueStep.AWAITING_AMOUNT.name());
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(listening, processing);
        when(voiceSessionMapper.claimForTurn(eq(USER_ID), eq(SESSION_ID), any())).thenReturn(1);
        when(dialogueTurnMapper.findNextSequenceNo(SESSION_ID)).thenReturn(2, 3);
        when(voiceTurnAnalysisPort.analyze(any())).thenReturn(analysis());
        when(voiceSessionMapper.completeTurn(USER_ID, SESSION_ID, DialogueStep.AWAITING_AMOUNT.name()))
                .thenReturn(1);

        VoiceTurnResponse response = service.process(USER_ID, SESSION_ID, request());

        assertEquals(DialogueStep.AWAITING_AMOUNT, response.getState());
        assertEquals("ASK_AMOUNT", response.getNextAction());
        verify(recipientService, never()).findCandidates(any(), any());
    }

    @Test
    void acceptsFocusedRecipientByVoiceWithoutCallingTheAnalysisPort() throws Exception {
        VoiceSessionVo listening = activeSession();
        listening.setFlowType(VoiceFlowType.TRANSFER.name());
        listening.setCurrentStep(DialogueStep.AWAITING_RECIPIENT.name());
        VoiceSessionVo processing = processingSession();
        processing.setFlowType(VoiceFlowType.TRANSFER.name());
        processing.setCurrentStep(DialogueStep.AWAITING_RECIPIENT.name());
        VoiceInteractionCardVo card = focusedRecipientCard();
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID))
                .thenReturn(listening, processing);
        when(voiceSessionMapper.claimForTurn(eq(USER_ID), eq(SESSION_ID), any())).thenReturn(1);
        when(dialogueTurnMapper.findNextSequenceNo(SESSION_ID)).thenReturn(2, 3);
        when(voiceInteractionCardMapper.findBySessionId(SESSION_ID)).thenReturn(card);
        when(voiceInteractionCardMapper.findBySessionIdForUpdate(SESSION_ID)).thenReturn(card);
        when(voiceInteractionCardMapper.replace(any(VoiceInteractionCardVo.class))).thenReturn(1);
        when(voiceSessionMapper.completeTurn(USER_ID, SESSION_ID, DialogueStep.AWAITING_AMOUNT.name()))
                .thenReturn(1);

        VoiceTurnResponse response = service.process(USER_ID, SESSION_ID, request("이걸로 할래"));

        assertEquals(DialogueStep.AWAITING_AMOUNT, response.getState());
        assertEquals("보낼 금액을 말씀해 주세요.", response.getTtsText());
        assertEquals("ASK_AMOUNT", response.getNextAction());
        verify(voiceTurnAnalysisPort, never()).analyze(any());
        ArgumentCaptor<VoiceInteractionCardVo> cardCaptor = ArgumentCaptor.forClass(VoiceInteractionCardVo.class);
        verify(voiceInteractionCardMapper).replace(cardCaptor.capture());
        assertEquals(card.getFocusedItemId(), cardCaptor.getValue().getConfirmedRecipientId());
        assertFalse(cardCaptor.getValue().isActive());
    }

    @Test
    void focusesTheSecondRecipientWhenTheUserNamesItsOrdinalBeforeAccepting() throws Exception {
        VoiceSessionVo listening = activeSession();
        listening.setFlowType(VoiceFlowType.TRANSFER.name());
        listening.setCurrentStep(DialogueStep.AWAITING_RECIPIENT.name());
        VoiceSessionVo processing = processingSession();
        processing.setFlowType(VoiceFlowType.TRANSFER.name());
        processing.setCurrentStep(DialogueStep.AWAITING_RECIPIENT.name());
        VoiceInteractionCardVo card = twoCandidateRecipientCard();
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID))
                .thenReturn(listening, processing);
        when(voiceSessionMapper.claimForTurn(eq(USER_ID), eq(SESSION_ID), any())).thenReturn(1);
        when(dialogueTurnMapper.findNextSequenceNo(SESSION_ID)).thenReturn(2, 3);
        when(voiceInteractionCardMapper.findBySessionId(SESSION_ID)).thenReturn(card);
        when(voiceSessionMapper.completeTurn(USER_ID, SESSION_ID, DialogueStep.AWAITING_RECIPIENT.name()))
                .thenReturn(1);

        VoiceTurnResponse response = service.process(USER_ID, SESSION_ID, request("두 번째"));

        assertEquals(DialogueStep.AWAITING_RECIPIENT, response.getState());
        assertEquals("김영희님에게 돈을 보내시려는 게 맞을까요?", response.getTtsText());
        assertEquals("50000000-0000-0000-0000-000000000002",
                response.getDisplayCard().path("focusedItemId").asText());
        assertEquals("RECONFIRM_INPUT", response.getNextAction());
        verify(voiceTurnAnalysisPort, never()).analyze(any());
    }

    @Test
    void keepsHeldStepAndDoesNotAnalyzeFurtherVoiceInput() throws Exception {
        VoiceSessionVo listening = backendTransferSession();
        listening.setCurrentStep(DialogueStep.HELD.name());
        VoiceSessionVo processing = backendTransferSession();
        processing.setCurrentStep(DialogueStep.HELD.name());
        processing.setStatus(VoiceSessionStatus.PROCESSING.name());
        VoiceInteractionCardVo card = new VoiceInteractionCardVo();
        card.setSessionId(SESSION_ID);
        card.setCardType("TRANSFER_HELD");
        card.setActive(true);
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID))
                .thenReturn(listening, processing);
        when(voiceSessionMapper.claimForTurn(eq(USER_ID), eq(SESSION_ID), any())).thenReturn(1);
        when(voiceInteractionCardMapper.findBySessionId(SESSION_ID)).thenReturn(card);
        when(dialogueTurnMapper.findNextSequenceNo(SESSION_ID)).thenReturn(2, 3);
        when(voiceSessionMapper.completeTurn(USER_ID, SESSION_ID, DialogueStep.HELD.name())).thenReturn(1);

        VoiceTurnResponse response = service.process(USER_ID, SESSION_ID, request("보내줘"));

        assertEquals(DialogueStep.HELD, response.getState());
        assertEquals("WAIT_GUARDIAN_VERIFICATION", response.getNextAction());
        verify(voiceTurnAnalysisPort, never()).analyze(any());
        verify(voiceSessionMapper).completeTurn(USER_ID, SESSION_ID, DialogueStep.HELD.name());
    }

    @Test
    void keepsHeldCardActiveAcrossTwoFurtherVoiceTurnsWithoutAnalysis() throws Exception {
        VoiceSessionVo listening = backendTransferSession();
        listening.setCurrentStep(DialogueStep.HELD.name());
        VoiceSessionVo processing = backendTransferSession();
        processing.setCurrentStep(DialogueStep.HELD.name());
        processing.setStatus(VoiceSessionStatus.PROCESSING.name());
        VoiceInteractionCardVo card = new VoiceInteractionCardVo();
        card.setSessionId(SESSION_ID);
        card.setCardType("TRANSFER_HELD");
        card.setActive(true);
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID))
                .thenReturn(listening, processing, listening, processing);
        when(voiceSessionMapper.claimForTurn(eq(USER_ID), eq(SESSION_ID), any())).thenReturn(1);
        when(voiceInteractionCardMapper.findBySessionId(SESSION_ID)).thenReturn(card);
        when(dialogueTurnMapper.findNextSequenceNo(SESSION_ID)).thenReturn(2, 3, 4, 5);
        when(voiceSessionMapper.completeTurn(USER_ID, SESSION_ID, DialogueStep.HELD.name())).thenReturn(1);

        VoiceTurnResponse first = service.process(USER_ID, SESSION_ID, request("보내줘"));
        VoiceTurnResponse second = service.process(USER_ID, SESSION_ID, request(
                "20000000-0000-0000-0000-000000000002", "아직인가요"));

        assertEquals(DialogueStep.HELD, first.getState());
        assertEquals("WAIT_GUARDIAN_VERIFICATION", first.getNextAction());
        assertEquals(DialogueStep.HELD, second.getState());
        assertEquals("WAIT_GUARDIAN_VERIFICATION", second.getNextAction());
        assertTrue(card.isActive());
        verify(voiceTurnAnalysisPort, never()).analyze(any());
        verify(voiceInteractionCardMapper, never()).deactivateActiveBySessionId(SESSION_ID);
        verify(voiceSessionMapper, times(2))
                .completeTurn(USER_ID, SESSION_ID, DialogueStep.HELD.name());
    }

    @Test
    void confirmsOnlyAfterExplicitReadbackApprovalThenRequestsPin() throws Exception {
        VoiceSessionVo listening = finalApprovalSession();
        VoiceSessionVo processing = processingFinalApprovalSession();
        VoiceInteractionCardVo card = transferReadbackCard();
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(listening, processing);
        when(voiceSessionMapper.claimForTurn(eq(USER_ID), eq(SESSION_ID), any())).thenReturn(1);
        when(dialogueTurnMapper.findNextSequenceNo(SESSION_ID)).thenReturn(2, 3);
        when(voiceInteractionCardMapper.findBySessionId(SESSION_ID)).thenReturn(card);
        when(voiceInteractionCardMapper.findBySessionIdForUpdate(SESSION_ID)).thenReturn(card);
        when(voiceInteractionCardMapper.replace(any(VoiceInteractionCardVo.class))).thenReturn(1);
        when(voiceSessionMapper.completeTurn(USER_ID, SESSION_ID, DialogueStep.WAITING_FINAL_APPROVAL.name()))
                .thenReturn(1);

        VoiceTurnResponse response = service.process(USER_ID, SESSION_ID, request("네"));

        assertEquals(DialogueStep.WAITING_FINAL_APPROVAL, response.getState());
        assertEquals("ASK_PIN", response.getNextAction());
        ArgumentCaptor<TransferConfirmRequest> confirmation = ArgumentCaptor.forClass(TransferConfirmRequest.class);
        verify(transferService).confirm(eq(java.util.UUID.fromString(USER_ID)),
                eq(java.util.UUID.fromString("70000000-0000-0000-0000-000000000001")), confirmation.capture());
        assertTrue(confirmation.getValue().getApproved());
        InOrder persistenceBeforeConfirmation = inOrder(voiceInteractionCardMapper, transferService);
        persistenceBeforeConfirmation.verify(voiceInteractionCardMapper).replace(any(VoiceInteractionCardVo.class));
        persistenceBeforeConfirmation.verify(transferService).confirm(any(), any(), any());
        verify(voiceTurnAnalysisPort, never()).analyze(any());
    }

    @Test
    void doesNotConfirmWhenFinalReadbackCardCannotBeUpdated() throws Exception {
        VoiceSessionVo listening = finalApprovalSession();
        VoiceSessionVo processing = processingFinalApprovalSession();
        VoiceInteractionCardVo card = transferReadbackCard();
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(listening, processing);
        when(voiceSessionMapper.claimForTurn(eq(USER_ID), eq(SESSION_ID), any())).thenReturn(1);
        when(dialogueTurnMapper.findNextSequenceNo(SESSION_ID)).thenReturn(2, 3);
        when(voiceInteractionCardMapper.findBySessionId(SESSION_ID)).thenReturn(card);
        when(voiceInteractionCardMapper.findBySessionIdForUpdate(SESSION_ID)).thenReturn(card);
        when(voiceInteractionCardMapper.replace(any(VoiceInteractionCardVo.class))).thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.process(USER_ID, SESSION_ID, request("네")));

        assertEquals("VOICE_TURN_CONFLICT", exception.getErrorCode().getCode());
        verify(transferService, never()).confirm(any(), any(), any());
    }

    @Test
    void cancelsTheTransferWhenTheUserRejectsReadback() throws Exception {
        VoiceSessionVo listening = finalApprovalSession();
        VoiceSessionVo processing = processingFinalApprovalSession();
        VoiceInteractionCardVo card = transferReadbackCard();
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(listening, processing);
        when(voiceSessionMapper.claimForTurn(eq(USER_ID), eq(SESSION_ID), any())).thenReturn(1);
        when(dialogueTurnMapper.findNextSequenceNo(SESSION_ID)).thenReturn(2, 3);
        when(voiceInteractionCardMapper.findBySessionId(SESSION_ID)).thenReturn(card);
        when(voiceInteractionCardMapper.findBySessionIdForUpdate(SESSION_ID)).thenReturn(card);
        when(voiceInteractionCardMapper.replace(any(VoiceInteractionCardVo.class))).thenReturn(1);
        when(voiceSessionMapper.closeOwned(eq(USER_ID), eq(SESSION_ID),
                eq(DialogueStep.CANCELLED.name()), any())).thenReturn(1);

        VoiceTurnResponse response = service.process(USER_ID, SESSION_ID, request("아니요"));

        assertEquals(DialogueStep.CANCELLED, response.getState());
        assertEquals("END_SESSION", response.getNextAction());
        ArgumentCaptor<TransferConfirmRequest> confirmation = ArgumentCaptor.forClass(TransferConfirmRequest.class);
        verify(transferService).confirm(eq(java.util.UUID.fromString(USER_ID)),
                eq(java.util.UUID.fromString("70000000-0000-0000-0000-000000000001")), confirmation.capture());
        assertFalse(confirmation.getValue().getApproved());
        verify(voiceTurnAnalysisPort, never()).analyze(any());
    }

    @Test
    void reasksForExplicitApprovalWithoutConfirmingTheTransfer() throws Exception {
        VoiceSessionVo listening = finalApprovalSession();
        VoiceSessionVo processing = processingFinalApprovalSession();
        VoiceInteractionCardVo card = transferReadbackCard();
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(listening, processing);
        when(voiceSessionMapper.claimForTurn(eq(USER_ID), eq(SESSION_ID), any())).thenReturn(1);
        when(dialogueTurnMapper.findNextSequenceNo(SESSION_ID)).thenReturn(2, 3);
        when(voiceInteractionCardMapper.findBySessionId(SESSION_ID)).thenReturn(card);
        when(transferService.get(any(), any())).thenReturn(canonicalReadback());
        when(voiceSessionMapper.completeTurn(USER_ID, SESSION_ID, DialogueStep.WAITING_FINAL_APPROVAL.name()))
                .thenReturn(1);

        VoiceTurnResponse response = service.process(USER_ID, SESSION_ID, request("응"));

        assertEquals(DialogueStep.WAITING_FINAL_APPROVAL, response.getState());
        assertEquals("ASK_FINAL_APPROVAL", response.getNextAction());
        verify(transferService, never()).confirm(any(), any(), any());
        verify(voiceTurnAnalysisPort, never()).analyze(any());
    }

    @Test
    void delegatesGeneralFinanceAccountInquiryToTheAccountResponseResolver() throws Exception {
        VoiceTurnAnalysisResult accountInquiry = accountInquiryAnalysis();
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID))
                .thenReturn(activeSession(), processingSession());
        when(voiceSessionMapper.claimForTurn(eq(USER_ID), eq(SESSION_ID), any())).thenReturn(1);
        when(dialogueTurnMapper.findNextSequenceNo(SESSION_ID)).thenReturn(2, 3);
        when(voiceTurnAnalysisPort.analyze(any())).thenReturn(accountInquiry);
        when(voiceSessionMapper.completeTurn(USER_ID, SESSION_ID, DialogueStep.AWAITING_INPUT.name()))
                .thenReturn(1);

        service.process(USER_ID, SESSION_ID, request("내 계좌를 보여줘"));

        verify(accountVoiceResponseResolver).resolve(USER_ID, accountInquiry);
    }

    @Test
    void reusesTheStoredAiResponseForAnIdenticalTurnId() throws Exception {
        DialogueTurnVo existingUserTurn = userTurn();
        DialogueTurnVo existingAiTurn = aiTurn();
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(activeSession());
        when(dialogueTurnMapper.findBySessionIdAndTurnId(SESSION_ID, TURN_ID)).thenReturn(existingUserTurn);
        when(dialogueTurnMapper.findBySessionIdAndSequenceNo(SESSION_ID, 3)).thenReturn(existingAiTurn);

        VoiceTurnResponse response = service.process(USER_ID, SESSION_ID, request());

        assertEquals(DialogueStep.AWAITING_AMOUNT, response.getState());
        assertEquals(SESSION_ID, response.getSessionId());
        assertEquals(TURN_ID, response.getTurnId());
        assertEquals("ASK_AMOUNT", response.getNextAction());
        assertEquals("TRANSFER_RECIPIENT_CANDIDATES", response.getRequestedFunction());
        assertEquals("김철수", response.getSlots().get("recipient"));
        verify(voiceSessionMapper, never()).claimForTurn(any(), any(), any());
        verify(voiceTurnAnalysisPort, never()).analyze(any());
        verify(dialogueTurnMapper, never()).insert(any());
    }

    @Test
    void reusesTheStoredAiResponseAfterTheSessionIsClosed() throws Exception {
        VoiceSessionVo closedSession = activeSession();
        closedSession.setStatus(VoiceSessionStatus.CLOSED.name());
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(closedSession);
        when(dialogueTurnMapper.findBySessionIdAndTurnId(SESSION_ID, TURN_ID)).thenReturn(userTurn());
        when(dialogueTurnMapper.findBySessionIdAndSequenceNo(SESSION_ID, 3)).thenReturn(aiTurn());

        VoiceTurnResponse response = service.process(USER_ID, SESSION_ID, request());

        assertStoredTurnResponse(response);
        verify(voiceSessionMapper, never()).claimForTurn(any(), any(), any());
        verify(voiceTurnAnalysisPort, never()).analyze(any());
        verify(dialogueTurnMapper, never()).insert(any());
    }

    @Test
    void reusesTheStoredAiResponseAfterTheSessionIsExpired() throws Exception {
        VoiceSessionVo expiredSession = activeSession();
        expiredSession.setStatus(VoiceSessionStatus.EXPIRED.name());
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(expiredSession);
        when(dialogueTurnMapper.findBySessionIdAndTurnId(SESSION_ID, TURN_ID)).thenReturn(userTurn());
        when(dialogueTurnMapper.findBySessionIdAndSequenceNo(SESSION_ID, 3)).thenReturn(aiTurn());

        VoiceTurnResponse response = service.process(USER_ID, SESSION_ID, request());

        assertStoredTurnResponse(response);
        verify(voiceSessionMapper, never()).claimForTurn(any(), any(), any());
        verify(voiceTurnAnalysisPort, never()).analyze(any());
        verify(dialogueTurnMapper, never()).insert(any());
    }

    @Test
    void expiresThenReusesTheStoredAiResponseForAnIdenticalTurnId() throws Exception {
        VoiceSessionVo expiredAtRequestTime = activeSession();
        expiredAtRequestTime.setTransferId("70000000-0000-0000-0000-000000000001");
        expiredAtRequestTime.setExpiresAt(java.time.LocalDateTime.ofInstant(CLOCK.instant(), ZoneId.of("UTC")));
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(expiredAtRequestTime);
        when(dialogueTurnMapper.findBySessionIdAndTurnId(SESSION_ID, TURN_ID)).thenReturn(userTurn());
        when(dialogueTurnMapper.findBySessionIdAndSequenceNo(SESSION_ID, 3)).thenReturn(aiTurn());

        VoiceTurnResponse response = service.process(USER_ID, SESSION_ID, request());

        assertStoredTurnResponse(response);
        verify(voiceSessionMapper).updateStatusAndStep(
                USER_ID, SESSION_ID, VoiceSessionStatus.EXPIRED.name(), DialogueStep.AWAITING_INPUT.name());
        verify(transferService).cancelUnexecuted(
                UUID.fromString(USER_ID), UUID.fromString("70000000-0000-0000-0000-000000000001"));
        verify(voiceInteractionCardMapper).deactivateActiveBySessionId(SESSION_ID);
        verify(voiceSessionMapper, never()).claimForTurn(any(), any(), any());
        verify(voiceTurnAnalysisPort, never()).analyze(any());
        verify(dialogueTurnMapper, never()).insert(any());
    }

    @Test
    void rejectsAReusedTurnIdWithDifferentTranscript() throws Exception {
        DialogueTurnVo existingUserTurn = userTurn();
        existingUserTurn.setTranscript("다른 요청");
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(activeSession());
        when(dialogueTurnMapper.findBySessionIdAndTurnId(SESSION_ID, TURN_ID)).thenReturn(existingUserTurn);

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.process(USER_ID, SESSION_ID, request()));

        assertEquals("VOICE_TURN_CONFLICT", exception.getErrorCode().getCode());
        verify(voiceTurnAnalysisPort, never()).analyze(any());
        verify(dialogueTurnMapper, never()).insert(any());
    }

    @Test
    void rejectsAReusedTurnIdWithDifferentSttConfidence() throws Exception {
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(activeSession());
        when(dialogueTurnMapper.findBySessionIdAndTurnId(SESSION_ID, TURN_ID)).thenReturn(userTurn());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.process(USER_ID, SESSION_ID,
                        request("김철수에게 오만원 보내줘", new BigDecimal("0.94"), DialogueInputType.VOICE)));

        assertEquals("VOICE_TURN_CONFLICT", exception.getErrorCode().getCode());
        verify(voiceTurnAnalysisPort, never()).analyze(any());
        verify(dialogueTurnMapper, never()).insert(any());
    }

    @Test
    void rejectsAReusedTurnIdWithDifferentInputType() throws Exception {
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(activeSession());
        when(dialogueTurnMapper.findBySessionIdAndTurnId(SESSION_ID, TURN_ID)).thenReturn(userTurn());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.process(USER_ID, SESSION_ID,
                        request("김철수에게 오만원 보내줘", new BigDecimal("0.95"), DialogueInputType.TEXT)));

        assertEquals("VOICE_TURN_CONFLICT", exception.getErrorCode().getCode());
        verify(voiceTurnAnalysisPort, never()).analyze(any());
        verify(dialogueTurnMapper, never()).insert(any());
    }

    @Test
    void rejectsAChangedRequestWithTheSameTurnIdAfterTheSessionIsClosed() throws Exception {
        VoiceSessionVo closedSession = activeSession();
        closedSession.setStatus(VoiceSessionStatus.CLOSED.name());
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(closedSession);
        when(dialogueTurnMapper.findBySessionIdAndTurnId(SESSION_ID, TURN_ID)).thenReturn(userTurn());

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.process(USER_ID, SESSION_ID, request("다른 요청")));

        assertEquals("VOICE_TURN_CONFLICT", exception.getErrorCode().getCode());
        verify(voiceTurnAnalysisPort, never()).analyze(any());
        verify(dialogueTurnMapper, never()).insert(any());
    }

    @Test
    void rejectsAChangedRequestWithTheSameTurnIdAfterTheSessionIsExpired() throws Exception {
        VoiceSessionVo expiredSession = activeSession();
        expiredSession.setStatus(VoiceSessionStatus.EXPIRED.name());
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(expiredSession);
        when(dialogueTurnMapper.findBySessionIdAndTurnId(SESSION_ID, TURN_ID)).thenReturn(userTurn());

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.process(USER_ID, SESSION_ID, request("다른 요청")));

        assertEquals("VOICE_TURN_CONFLICT", exception.getErrorCode().getCode());
        verify(voiceTurnAnalysisPort, never()).analyze(any());
        verify(dialogueTurnMapper, never()).insert(any());
    }

    @Test
    void hidesOtherUsersSessionAsNotFound() throws Exception {
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.process(USER_ID, SESSION_ID, request()));

        assertEquals("VOICE_SESSION_NOT_FOUND", exception.getErrorCode().getCode());
    }

    @Test
    void expiresAndBlocksNewTurnsWhileClosedSessionsRemainBlocked() throws Exception {
        VoiceSessionVo expiredSession = activeSession();
        expiredSession.setExpiresAt(java.time.LocalDateTime.ofInstant(
                CLOCK.instant().minusSeconds(1), ZoneId.of("UTC")));
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(expiredSession);

        BusinessException expired = assertThrows(
                BusinessException.class, () -> service.process(USER_ID, SESSION_ID, request()));

        assertEquals("VOICE_TURN_CONFLICT", expired.getErrorCode().getCode());
        verify(voiceSessionMapper, never()).claimForTurn(any(), any(), any());
        verify(voiceSessionMapper).updateStatusAndStep(
                USER_ID, SESSION_ID, VoiceSessionStatus.EXPIRED.name(), DialogueStep.AWAITING_INPUT.name());
        verify(voiceInteractionCardMapper).deactivateActiveBySessionId(SESSION_ID);

        VoiceSessionVo closedSession = activeSession();
        closedSession.setStatus(VoiceSessionStatus.CLOSED.name());
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(closedSession);

        BusinessException closed = assertThrows(
                BusinessException.class, () -> service.process(USER_ID, SESSION_ID, request()));

        assertEquals("VOICE_TURN_CONFLICT", closed.getErrorCode().getCode());
        verify(voiceSessionMapper, never()).claimForTurn(any(), any(), any());
        verify(voiceTurnAnalysisPort, never()).analyze(any());
        verify(dialogueTurnMapper, never()).insert(any());
        verify(voiceSessionMapper, never()).completeTurn(any(), any(), any());
    }

    @Test
    void preservesTheDialogueStepWhenExpiringAnActiveSession() throws Exception {
        VoiceSessionVo expiredSession = activeSession();
        expiredSession.setCurrentStep(DialogueStep.AWAITING_AMOUNT.name());
        expiredSession.setExpiresAt(java.time.LocalDateTime.ofInstant(
                CLOCK.instant().minusSeconds(1), ZoneId.of("UTC")));
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(expiredSession);

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.process(USER_ID, SESSION_ID, request()));

        assertEquals("VOICE_TURN_CONFLICT", exception.getErrorCode().getCode());
        verify(voiceSessionMapper).updateStatusAndStep(
                USER_ID, SESSION_ID, VoiceSessionStatus.EXPIRED.name(), DialogueStep.AWAITING_AMOUNT.name());
        verify(voiceInteractionCardMapper).deactivateActiveBySessionId(SESSION_ID);
    }

    @Test
    void rejectsAnotherTurnWhileTheSessionIsAlreadyProcessing() throws Exception {
        VoiceSessionVo processingSession = activeSession();
        processingSession.setStatus(VoiceSessionStatus.PROCESSING.name());
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(processingSession);
        when(voiceSessionMapper.claimForTurn(eq(USER_ID), eq(SESSION_ID), any())).thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.process(USER_ID, SESSION_ID, request()));

        assertEquals("VOICE_TURN_CONFLICT", exception.getErrorCode().getCode());
        verify(voiceTurnAnalysisPort, never()).analyze(any());
        verify(dialogueTurnMapper, never()).insert(any());
    }

    @Test
    void restoresThePreviousSessionStatusWhenAnalysisFails() throws Exception {
        RuntimeException analysisFailure = new IllegalStateException("OpenAI is unavailable");
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(activeSession());
        when(voiceSessionMapper.claimForTurn(eq(USER_ID), eq(SESSION_ID), any())).thenReturn(1);
        when(voiceTurnAnalysisPort.analyze(any())).thenThrow(analysisFailure);

        RuntimeException exception = assertThrows(
                RuntimeException.class, () -> service.process(USER_ID, SESSION_ID, request()));

        assertEquals(analysisFailure, exception);
        verify(voiceSessionMapper).restoreTurnClaim(
                USER_ID, SESSION_ID, VoiceSessionStatus.LISTENING.name());
        verify(dialogueTurnMapper, never()).insert(any());
    }

    @Test
    void mapsMySqlNowaitLockFailureToTurnConflict() throws Exception {
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenThrow(
                new PersistenceException(
                        "voice session is already locked",
                        new SQLException("NOWAIT lock failed", "HY000", 3572)));

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.process(USER_ID, SESSION_ID, request()));

        assertEquals("VOICE_TURN_CONFLICT", exception.getErrorCode().getCode());
        verify(voiceTurnAnalysisPort, never()).analyze(any());
    }

    @Test
    void reasksWithoutCallingAmountValidationOrLlmWhenAzureAmountIsUnsafe() {
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID))
                .thenReturn(backendTransferSession(), processingBackendTransferSession());
        when(voiceSessionMapper.claimForTurn(eq(USER_ID), eq(SESSION_ID), any())).thenReturn(1);
        when(dialogueTurnMapper.findNextSequenceNo(SESSION_ID)).thenReturn(2, 3);
        when(amountCandidateGenerator.decide(any())).thenReturn(new AmountCandidateDecision(
                AmountCandidateDecisionType.REASK, null, List.of(), false));
        when(voiceSessionMapper.completeTurn(USER_ID, SESSION_ID, DialogueStep.AWAITING_INPUT.name()))
                .thenReturn(1);

        VoiceTurnResponse response = service.processAzureTransferFinal(
                USER_ID, SESSION_ID, TURN_ID, azureResult());

        assertEquals(DialogueStep.AWAITING_INPUT, response.getState());
        assertEquals("REASK_INPUT", response.getNextAction());
        verify(transferService, never()).validateAmount(any());
        verify(transferService, never()).prepare(any(), any());
        verify(transferService, never()).confirm(any(), any(), any());
        verify(transferService, never()).authenticate(any(), any(), any(), any());
        verify(transferService, never()).execute(any(), any(), any(), any());
        verify(voiceTurnAnalysisPort, never()).analyze(any());
    }

    @Test
    void reconfirmsBeforeDraftOrRiskProcessingWhenAzureCandidatesAreAmbiguous() {
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID))
                .thenReturn(backendTransferSession(), processingBackendTransferSession());
        when(voiceSessionMapper.claimForTurn(eq(USER_ID), eq(SESSION_ID), any())).thenReturn(1);
        when(dialogueTurnMapper.findNextSequenceNo(SESSION_ID)).thenReturn(2, 3);
        when(amountCandidateGenerator.decide(any())).thenReturn(new AmountCandidateDecision(
                AmountCandidateDecisionType.RECONFIRM, null, List.of(300_000L, 400_000L), true));
        when(transferService.validateAmount(any())).thenReturn(new AmountValidationResponse(
                null, List.of(300_000L, 400_000L), null, true));
        when(voiceSessionMapper.completeTurn(USER_ID, SESSION_ID, DialogueStep.RECONFIRMING.name()))
                .thenReturn(1);

        VoiceTurnResponse response = service.processAzureTransferFinal(
                USER_ID, SESSION_ID, TURN_ID, azureResult());

        assertEquals(DialogueStep.RECONFIRMING, response.getState());
        assertEquals(List.of(300_000L, 400_000L), response.getSlots().get("amountCandidates"));
        assertEquals("RECONFIRM_INPUT", response.getNextAction());
        verify(transferService).validateAmount(any());
        verify(transferService, never()).prepare(any(), any());
        verify(transferService, never()).confirm(any(), any(), any());
        verify(transferService, never()).authenticate(any(), any(), any(), any());
        verify(transferService, never()).execute(any(), any(), any(), any());
        verify(voiceTurnAnalysisPort, never()).analyze(any());
    }

    @Test
    void guidanceCommandDuringFinalApprovalDoesNotConfirmTransferOrCallLlm() throws Exception {
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID))
                .thenReturn(finalApprovalSession(), processingFinalApprovalSession());
        when(voiceSessionMapper.claimForTurn(eq(USER_ID), eq(SESSION_ID), any())).thenReturn(1);
        when(voiceInteractionCardMapper.findBySessionId(SESSION_ID)).thenReturn(transferReadbackCard());
        when(transferService.get(
                UUID.fromString(USER_ID), UUID.fromString("70000000-0000-0000-0000-000000000001")))
                .thenReturn(canonicalReadback());
        when(dialogueTurnMapper.findNextSequenceNo(SESSION_ID)).thenReturn(6, 7);
        when(voiceSessionMapper.completeTurn(USER_ID, SESSION_ID, DialogueStep.WAITING_FINAL_APPROVAL.name()))
                .thenReturn(1);

        VoiceTurnResponse response = service.processAzureTransferFinal(
                USER_ID, SESSION_ID, TURN_ID,
                new AzureSpeechDetailedResult("천천히 말해줘", new BigDecimal("0.95"), List.of()));

        assertEquals(DialogueStep.WAITING_FINAL_APPROVAL, response.getState());
        assertEquals("ASK_FINAL_APPROVAL", response.getNextAction());
        verify(transferService, never()).confirm(any(), any(), any());
        verify(transferService, never()).authenticate(any(), any(), any(), any());
        verify(transferService, never()).execute(any(), any(), any(), any());
        verify(voiceTurnAnalysisPort, never()).analyze(any());
    }

    @Test
    void streamingGuidanceCommandPreservesTheCommandAndUpdatesTheNextSessionStep() {
        VoiceSessionVo claimed = processingFinalApprovalSession();
        claimed.setActiveInputTurnId(TURN_ID);
        claimed.setLifecycleGeneration(4);
        VoiceSessionVo current = processingFinalApprovalSession();
        current.setActiveInputTurnId(TURN_ID);
        current.setLifecycleGeneration(4);
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(claimed, current);
        when(voiceInteractionCardMapper.findBySessionId(SESSION_ID)).thenReturn(transferReadbackCard());
        when(transferService.get(
                UUID.fromString(USER_ID), UUID.fromString("70000000-0000-0000-0000-000000000001")))
                .thenReturn(canonicalReadback());
        when(dialogueTurnMapper.findNextSequenceNo(SESSION_ID)).thenReturn(6, 7);
        when(voiceSessionMapper.completeStreamTurnWithAi(
                        eq(USER_ID),
                        eq(SESSION_ID),
                        eq(TURN_ID),
                        eq(4L),
                        any(),
                        eq(DialogueStep.WAITING_FINAL_APPROVAL.name()),
                        any()))
                .thenReturn(1);

        VoiceTurnResponse response = service.processAzureTransferFinal(
                USER_ID,
                SESSION_ID,
                TURN_ID,
                4,
                new AzureSpeechDetailedResult("천천히 말해줘", new BigDecimal("0.95"), List.of()));

        assertEquals(DialogueStep.WAITING_FINAL_APPROVAL, response.getState());
        assertEquals("ASK_FINAL_APPROVAL", response.getNextAction());
        verify(voiceTurnAnalysisPort, never()).analyze(any());
        verify(voiceSessionMapper).completeStreamTurnWithAi(
                eq(USER_ID),
                eq(SESSION_ID),
                eq(TURN_ID),
                eq(4L),
                any(),
                eq(DialogueStep.WAITING_FINAL_APPROVAL.name()),
                any());
    }

    @Test
    void guidanceCommandDuringRiskCheckDoesNotProcessTheCommandAsARiskAnswer() throws Exception {
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID))
                .thenReturn(riskCheckSession(), processingRiskCheckSession());
        when(voiceSessionMapper.claimForTurn(eq(USER_ID), eq(SESSION_ID), any())).thenReturn(1);
        when(voiceInteractionCardMapper.findBySessionId(SESSION_ID)).thenReturn(transferRiskCheckCard());
        when(transferService.get(
                UUID.fromString(USER_ID), UUID.fromString("70000000-0000-0000-0000-000000000001")))
                .thenReturn(canonicalReadback());
        when(dialogueTurnMapper.findNextSequenceNo(SESSION_ID)).thenReturn(6, 7);
        when(voiceSessionMapper.completeTurn(USER_ID, SESSION_ID, DialogueStep.RISK_CHECK.name()))
                .thenReturn(1);

        VoiceTurnResponse response = service.processAzureTransferFinal(
                USER_ID, SESSION_ID, TURN_ID,
                new AzureSpeechDetailedResult("천천히 말해줘", new BigDecimal("0.95"), List.of()));

        assertEquals(DialogueStep.RISK_CHECK, response.getState());
        assertEquals("NONE", response.getNextAction());
        verify(riskScoreService, never()).checkContext(any(), any(), any());
        verify(voiceTurnAnalysisPort, never()).analyze(any());
    }

    @Test
    void rejectsAmountAcceptanceWhenThePersistedFocusedCandidateDoesNotMatch() throws Exception {
        VoiceSessionVo listening = activeSession();
        listening.setCurrentStep(DialogueStep.RECONFIRMING.name());
        listening.setFromAccountId("60000000-0000-0000-0000-000000000001");
        VoiceSessionVo processing = processingSession();
        processing.setCurrentStep(DialogueStep.RECONFIRMING.name());
        processing.setFromAccountId("60000000-0000-0000-0000-000000000001");
        VoiceInteractionCardVo selected = amountCard("amount-a", 50_000L);
        VoiceInteractionCardVo changed = amountCard("amount-a", 70_000L);
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID))
                .thenReturn(listening, processing);
        when(voiceSessionMapper.claimForTurn(eq(USER_ID), eq(SESSION_ID), any())).thenReturn(1);
        when(voiceInteractionCardMapper.findBySessionId(SESSION_ID)).thenReturn(selected);
        when(voiceInteractionCardMapper.findBySessionIdForUpdate(SESSION_ID)).thenReturn(changed);
        when(transferService.validateAmount(any())).thenReturn(new AmountValidationResponse(
                50_000L, List.of(50_000L), 50_000L, false));
        when(transferService.prepare(any(), any())).thenReturn(new TransferPrepareResponse(
                java.util.UUID.fromString("30000000-0000-0000-0000-000000000001"), "DRAFT", "RISK_CHECK",
                new TransferRecipientResponse(
                        java.util.UUID.fromString("40000000-0000-0000-0000-000000000001"),
                        "김철수", "004", "***-***-1234"),
                50_000L, false, "김철수 님에게 50,000원을 보내시겠어요?", null));
        when(voiceSessionMapper.updateTransferId(any(), any(), any())).thenReturn(1);
        when(riskScoreService.assess(any(), any())).thenReturn(new RiskScoreResponse(
                0, 0, "LOW", "RULE", false, List.of(), null, "ALLOW", false));
        when(transferService.get(any(), any())).thenReturn(new TransferResponse(
                java.util.UUID.fromString("30000000-0000-0000-0000-000000000001"), "DRAFT", "RISK_CHECK",
                java.util.UUID.fromString("60000000-0000-0000-0000-000000000001"),
                new TransferRecipientResponse(
                        java.util.UUID.fromString("40000000-0000-0000-0000-000000000001"),
                        "김철수", "004", "***-***-1234"),
                50_000L, null, List.of(), false,
                "김철수 님에게 50,000원을 보내시겠어요?", null, null, null));
        when(dialogueTurnMapper.findNextSequenceNo(SESSION_ID)).thenReturn(2, 3);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.process(USER_ID, SESSION_ID, request("네")));

        assertEquals("VOICE_TURN_CONFLICT", exception.getErrorCode().getCode());
        verify(voiceInteractionCardMapper, never()).replace(any());
    }

    @Test
    void blocksRawTurnEndpointPathForBackendStreamTransferSessions() {
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID))
                .thenReturn(backendTransferSession());
        when(voiceSessionMapper.claimForTurn(eq(USER_ID), eq(SESSION_ID), any())).thenReturn(1);

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.process(USER_ID, SESSION_ID, request()));

        assertEquals("INVALID_REQUEST", exception.getErrorCode().getCode());
        verify(voiceTurnAnalysisPort, never()).analyze(any());
        verify(transferService, never()).validateAmount(any());
        verify(voiceSessionMapper).restoreTurnClaim(
                USER_ID, SESSION_ID, VoiceSessionStatus.LISTENING.name());
    }

    @Test
    void acceptsTextFallbackOnBackendStreamTransferSessions() throws Exception {
        VoiceSessionVo listening = backendTransferSession();
        VoiceSessionVo processing = processingBackendTransferSession();
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID))
                .thenReturn(listening, processing);
        when(voiceSessionMapper.claimForTurn(eq(USER_ID), eq(SESSION_ID), any())).thenReturn(1);
        when(dialogueTurnMapper.findNextSequenceNo(SESSION_ID)).thenReturn(2, 3);
        when(voiceTurnAnalysisPort.analyze(any())).thenReturn(analysis());
        when(voiceSessionMapper.completeTurn(USER_ID, SESSION_ID, DialogueStep.AWAITING_AMOUNT.name()))
                .thenReturn(1);

        VoiceTurnResponse response = service.process(
                USER_ID,
                SESSION_ID,
                request("김철수에게 오만원 보내줘", BigDecimal.ONE, DialogueInputType.TEXT));

        assertEquals(DialogueStep.AWAITING_AMOUNT, response.getState());
        verify(voiceTurnAnalysisPort).analyze(any());
        verify(dialogueTurnMapper, times(2)).insert(any());
    }

    @Test
    void acceptsTextGuidanceCommandsOnBackendStreamTransferSessions() throws Exception {
        VoiceSessionVo listening = backendTransferSession();
        listening.setCurrentStep(DialogueStep.AWAITING_CONTINUATION.name());
        VoiceSessionVo processing = processingBackendTransferSession();
        processing.setCurrentStep(DialogueStep.AWAITING_CONTINUATION.name());
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID))
                .thenReturn(listening, processing);
        when(voiceSessionMapper.claimForTurn(eq(USER_ID), eq(SESSION_ID), any())).thenReturn(1);
        when(dialogueTurnMapper.findNextSequenceNo(SESSION_ID)).thenReturn(2, 3);
        when(voiceSessionMapper.completeTurn(
                USER_ID, SESSION_ID, DialogueStep.AWAITING_CONTINUATION.name())).thenReturn(1);

        VoiceTurnResponse response = service.process(
                USER_ID,
                SESSION_ID,
                request("천천히 말해줘", BigDecimal.ONE, DialogueInputType.TEXT));

        assertEquals(DialogueStep.AWAITING_CONTINUATION, response.getState());
        assertEquals("ASK_CONTINUATION", response.getNextAction());
        verify(voiceTurnAnalysisPort, never()).analyze(any());
        verify(dialogueTurnMapper, never()).findLatestBusinessAiTurn(SESSION_ID);
    }

    @Test
    void blocksGuidanceCommandsOnTheRawBackendStreamTransferPath() throws Exception {
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID))
                .thenReturn(backendTransferSession());
        when(voiceSessionMapper.claimForTurn(eq(USER_ID), eq(SESSION_ID), any())).thenReturn(1);

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.process(USER_ID, SESSION_ID, request("천천히 말해줘")));

        assertEquals("INVALID_REQUEST", exception.getErrorCode().getCode());
        verify(voiceTurnAnalysisPort, never()).analyze(any());
        verify(voiceSessionMapper).restoreTurnClaim(
                USER_ID, SESSION_ID, VoiceSessionStatus.LISTENING.name());
    }

    @Test
    void resumesTheStoredProgressForCasualAffirmationWithoutCallingTheLlm() throws Exception {
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID))
                .thenReturn(continuationSession(), processingContinuationSession());
        when(voiceSessionMapper.claimForTurn(eq(USER_ID), eq(SESSION_ID), any())).thenReturn(1);
        when(dialogueTurnMapper.findLatestBySessionId(SESSION_ID)).thenReturn(continuationPrompt());
        when(dialogueTurnMapper.findLatestBusinessAiTurn(SESSION_ID)).thenReturn(aiTurn());
        when(dialogueTurnMapper.findNextSequenceNo(SESSION_ID)).thenReturn(6, 7);
        when(voiceSessionMapper.completeTurn(USER_ID, SESSION_ID, DialogueStep.AWAITING_AMOUNT.name()))
                .thenReturn(1);

        VoiceTurnResponse response = service.process(USER_ID, SESSION_ID, request("응."));

        assertEquals(DialogueStep.AWAITING_AMOUNT, response.getState());
        assertEquals("좋아요. 김철수님께 돈 보내기를 진행하고 있어요. 보낼 금액을 다시 말씀해 주세요.",
                response.getTtsText());
        verify(voiceTurnAnalysisPort, never()).analyze(any());
    }

    @Test
    void guidanceCommandDuringContinuationKeepsTheContinuationQuestion() throws Exception {
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID))
                .thenReturn(continuationSession(), processingContinuationSession());
        when(voiceSessionMapper.claimForTurn(eq(USER_ID), eq(SESSION_ID), any())).thenReturn(1);
        when(dialogueTurnMapper.findNextSequenceNo(SESSION_ID)).thenReturn(6, 7);
        when(voiceSessionMapper.completeTurn(USER_ID, SESSION_ID, DialogueStep.AWAITING_CONTINUATION.name()))
                .thenReturn(1);

        VoiceTurnResponse response = service.process(USER_ID, SESSION_ID, request("천천히 말해줘"));

        assertEquals(DialogueStep.AWAITING_CONTINUATION, response.getState());
        assertEquals("ASK_CONTINUATION", response.getNextAction());
        verify(voiceTurnAnalysisPort, never()).analyze(any());
        verify(dialogueTurnMapper, never()).findLatestBusinessAiTurn(SESSION_ID);
    }

    @Test
    void closesTheSessionWhenTheUserDeclinesToContinue() throws Exception {
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID))
                .thenReturn(continuationSession(), processingContinuationSession());
        when(voiceSessionMapper.claimForTurn(eq(USER_ID), eq(SESSION_ID), any())).thenReturn(1);
        when(dialogueTurnMapper.findLatestBySessionId(SESSION_ID)).thenReturn(continuationPrompt());
        when(dialogueTurnMapper.findNextSequenceNo(SESSION_ID)).thenReturn(6, 7);
        when(voiceSessionMapper.closeOwned(eq(USER_ID), eq(SESSION_ID),
                eq(DialogueStep.CANCELLED.name()), any())).thenReturn(1);

        VoiceTurnResponse response = service.process(USER_ID, SESSION_ID, request("아니요"));

        assertEquals(DialogueStep.CANCELLED, response.getState());
        verify(voiceSessionMapper).closeOwned(
                eq(USER_ID), eq(SESSION_ID), eq(DialogueStep.CANCELLED.name()), any());
        verify(voiceTurnAnalysisPort, never()).analyze(any());
    }

    @Test
    void resumesTheBusinessPromptEvenAfterAContinuationClarification() throws Exception {
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID))
                .thenReturn(continuationSession(), processingContinuationSession());
        when(voiceSessionMapper.claimForTurn(eq(USER_ID), eq(SESSION_ID), any())).thenReturn(1);
        when(dialogueTurnMapper.findLatestBySessionId(SESSION_ID)).thenReturn(clarifiedContinuationPrompt());
        when(dialogueTurnMapper.findLatestBusinessAiTurn(SESSION_ID)).thenReturn(aiTurn());
        when(dialogueTurnMapper.findNextSequenceNo(SESSION_ID)).thenReturn(8, 9);
        when(voiceSessionMapper.completeTurn(USER_ID, SESSION_ID, DialogueStep.AWAITING_AMOUNT.name()))
                .thenReturn(1);

        VoiceTurnResponse response = service.process(USER_ID, SESSION_ID, request("네"));

        assertEquals(DialogueStep.AWAITING_AMOUNT, response.getState());
        verify(dialogueTurnMapper).findLatestBusinessAiTurn(SESSION_ID);
        verify(dialogueTurnMapper, never()).findBySessionIdAndSequenceNo(SESSION_ID, 6);
    }

    @Test
    void replacesAnyLlmAmountAndCandidatesWithTheAzureConfirmedAmount() throws Exception {
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID))
                .thenReturn(backendTransferSession(), processingBackendTransferSession());
        when(voiceSessionMapper.claimForTurn(eq(USER_ID), eq(SESSION_ID), any())).thenReturn(1);
        when(dialogueTurnMapper.findNextSequenceNo(SESSION_ID)).thenReturn(2, 3);
        when(amountCandidateGenerator.decide(any())).thenReturn(new AmountCandidateDecision(
                AmountCandidateDecisionType.CONFIRMED, 50_000L, List.of(50_000L), false));
        when(transferService.validateAmount(any())).thenReturn(new AmountValidationResponse(
                50_000L, List.of(50_000L), 50_000L, false));
        when(voiceTurnAnalysisPort.analyze(any())).thenReturn(analysisWithUntrustedAmount());
        when(voiceSessionMapper.completeTurn(USER_ID, SESSION_ID, DialogueStep.AWAITING_RECIPIENT.name()))
                .thenReturn(1);

        VoiceTurnResponse response = service.processAzureTransferFinal(
                USER_ID, SESSION_ID, TURN_ID, azureResult());

        assertEquals(50_000L, response.getSlots().get("amount"));
        assertEquals(List.of(50_000L), response.getSlots().get("amountCandidates"));
        assertEquals(50_000L, response.getDisplayCard().get("amount").longValue());
        assertEquals(1, response.getDisplayCard().get("amountCandidates").size());
        assertEquals(50_000L, response.getDisplayCard().get("amountCandidates").get(0).longValue());
        assertFalse(response.getDisplayCard().toString().contains("900000"));

        ArgumentCaptor<DialogueTurnVo> turns = ArgumentCaptor.forClass(DialogueTurnVo.class);
        verify(dialogueTurnMapper, times(2)).insert(turns.capture());
        JsonNode persistedDisplayCard = objectMapper.readTree(turns.getAllValues().get(1).getDisplayCard());
        assertEquals(50_000L, persistedDisplayCard.get("amount").longValue());
        assertEquals(1, persistedDisplayCard.get("amountCandidates").size());
        assertEquals(50_000L, persistedDisplayCard.get("amountCandidates").get(0).longValue());
        assertFalse(persistedDisplayCard.toString().contains("900000"));
    }

    @Test
    void retainsTheAzureValidatedAmountOnTheRecipientCard() throws Exception {
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID))
                .thenReturn(backendTransferSession(), processingBackendTransferSession());
        when(voiceSessionMapper.claimForTurn(eq(USER_ID), eq(SESSION_ID), any())).thenReturn(1);
        when(dialogueTurnMapper.findNextSequenceNo(SESSION_ID)).thenReturn(2, 3);
        when(amountCandidateGenerator.decide(any())).thenReturn(new AmountCandidateDecision(
                AmountCandidateDecisionType.CONFIRMED, 50_000L, List.of(50_000L), false));
        when(transferService.validateAmount(any())).thenReturn(new AmountValidationResponse(
                50_000L, List.of(50_000L), 50_000L, false));
        when(voiceTurnAnalysisPort.analyze(any())).thenReturn(analysisWithRecipientAndUntrustedAmount());
        when(recipientService.findCandidates(any(), any())).thenReturn(List.of(new RecipientCandidateResponse(
                java.util.UUID.fromString("50000000-0000-0000-0000-000000000001"),
                "김철수", "아들", "004", "***-***-1234", "HISTORY", null)));
        when(voiceSessionMapper.completeTurn(USER_ID, SESSION_ID, DialogueStep.AWAITING_RECIPIENT.name()))
                .thenReturn(1);

        VoiceTurnResponse response = service.processAzureTransferFinal(
                USER_ID, SESSION_ID, TURN_ID, azureResult());

        assertEquals(DialogueStep.AWAITING_RECIPIENT, response.getState());
        assertEquals("RECIPIENT_CANDIDATES", response.getDisplayCard().path("type").asText());
        assertEquals(50_000L, response.getDisplayCard()
                .path("pendingAmountCandidates").get(0).longValue());
        verify(transferService, never()).prepare(any(), any());
    }

    @Test
    void recipientVoiceAcceptanceMovesToAmountReconfirmationWithoutPreparingTransfer() throws Exception {
        VoiceSessionVo listening = backendTransferSession();
        listening.setCurrentStep(DialogueStep.AWAITING_RECIPIENT.name());
        VoiceSessionVo processing = processingBackendTransferSession();
        processing.setCurrentStep(DialogueStep.AWAITING_RECIPIENT.name());
        VoiceInteractionCardVo card = focusedRecipientCard();
        card.setCandidateItems("""
                [{"id":"50000000-0000-0000-0000-000000000001","displayName":"김철수",
                  "pendingAmountCandidates":[50000]}]
                """);
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(listening, processing);
        when(voiceSessionMapper.claimForTurn(eq(USER_ID), eq(SESSION_ID), any())).thenReturn(1);
        when(dialogueTurnMapper.findNextSequenceNo(SESSION_ID)).thenReturn(2, 3);
        when(voiceInteractionCardMapper.findBySessionId(SESSION_ID)).thenReturn(card);
        when(voiceInteractionCardMapper.findBySessionIdForUpdate(SESSION_ID)).thenReturn(card);
        when(voiceInteractionCardMapper.replace(any(VoiceInteractionCardVo.class))).thenReturn(1);
        when(voiceSessionMapper.completeTurn(USER_ID, SESSION_ID, DialogueStep.RECONFIRMING.name()))
                .thenReturn(1);

        VoiceTurnResponse response = service.processAzureTransferFinal(USER_ID, SESSION_ID, TURN_ID,
                new AzureSpeechDetailedResult("네", new BigDecimal("0.95"), List.of()));

        assertEquals(DialogueStep.RECONFIRMING, response.getState());
        assertEquals("AMOUNT_RECONFIRM", response.getDisplayCard().path("type").asText());
        assertEquals(50_000L, response.getDisplayCard().path("amountCandidates").get(0).longValue());
        verify(transferService, never()).prepare(any(), any());
    }

    @Test
    void returnsConflictForConcurrentTurnWhileAnalysisIsStillBlocked() throws Exception {
        CountDownLatch analysisStarted = new CountDownLatch(1);
        CountDownLatch releaseAnalysis = new CountDownLatch(1);
        VoiceTurnRequest request = request();

        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID))
                .thenReturn(activeSession(), processingSession(), processingSession());
        when(voiceSessionMapper.claimForTurn(eq(USER_ID), eq(SESSION_ID), any())).thenReturn(1, 0);
        when(dialogueTurnMapper.findNextSequenceNo(SESSION_ID)).thenReturn(2, 3);
        when(voiceTurnAnalysisPort.analyze(any())).thenAnswer(invocation -> {
            analysisStarted.countDown();
            if (!releaseAnalysis.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Test did not release the blocked analysis.");
            }
            return analysis();
        });
        when(voiceSessionMapper.completeTurn(USER_ID, SESSION_ID, DialogueStep.AWAITING_AMOUNT.name()))
                .thenReturn(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<VoiceTurnResponse> firstRequest = executor.submit(
                    () -> service.process(USER_ID, SESSION_ID, request));
            assertTrue(analysisStarted.await(1, TimeUnit.SECONDS));
            verify(transactionManager, times(1)).commit(any());

            Future<BusinessException> concurrentRequest = executor.submit(() -> assertThrows(
                    BusinessException.class, () -> service.process(USER_ID, SESSION_ID, request)));
            BusinessException exception = concurrentRequest.get(1, TimeUnit.SECONDS);

            assertEquals("VOICE_TURN_CONFLICT", exception.getErrorCode().getCode());
            releaseAnalysis.countDown();
            assertEquals(DialogueStep.AWAITING_AMOUNT, firstRequest.get(1, TimeUnit.SECONDS).getState());
        } finally {
            releaseAnalysis.countDown();
            executor.shutdownNow();
        }
    }

    private VoiceTurnRequest request() throws Exception {
        return request("김철수에게 오만원 보내줘");
    }

    private VoiceTurnRequest request(String transcript) throws Exception {
        return request(transcript, new BigDecimal("0.95"), DialogueInputType.VOICE);
    }

    private VoiceTurnRequest request(String turnId, String transcript) throws Exception {
        return objectMapper.readValue(
                "{\"turnId\":\"" + turnId + "\",\"transcript\":\"" + transcript + "\","
                        + "\"sttConfidence\":0.95,\"inputType\":\"VOICE\"}",
                VoiceTurnRequest.class);
    }

    private VoiceTurnRequest request(
            String transcript, BigDecimal sttConfidence, DialogueInputType inputType) throws Exception {
        return objectMapper.readValue(
                "{\"turnId\":\"" + TURN_ID + "\",\"transcript\":\"" + transcript + "\","
                        + "\"sttConfidence\":" + sttConfidence + ",\"inputType\":\""
                        + inputType.name() + "\"}",
                VoiceTurnRequest.class);
    }

    private void assertStoredTurnResponse(VoiceTurnResponse response) {
        assertEquals(TURN_ID, response.getTurnId());
        assertEquals(DialogueStep.AWAITING_AMOUNT, response.getState());
        assertEquals("김철수 님에게 보낼 금액을 말씀해 주세요.", response.getTtsText());
        assertEquals("<speak>김철수 님에게 보낼 금액을 말씀해 주세요.</speak>", response.getTtsSsml());
        assertEquals(Map.of("recipient", "김철수"), response.getSlots());
        assertEquals("ASK_AMOUNT", response.getNextAction());
        assertEquals("TRANSFER_RECIPIENT_CANDIDATES", response.getRequestedFunction());
    }

    private VoiceSessionVo activeSession() {
        VoiceSessionVo voiceSession = new VoiceSessionVo();
        voiceSession.setSessionId(SESSION_ID);
        voiceSession.setUserId(USER_ID);
        voiceSession.setStatus(VoiceSessionStatus.LISTENING.name());
        voiceSession.setCurrentStep(DialogueStep.AWAITING_INPUT.name());
        voiceSession.setFlowType(VoiceFlowType.GENERAL_FINANCE.name());
        voiceSession.setExpiresAt(java.time.LocalDateTime.ofInstant(
                CLOCK.instant().plusSeconds(60), ZoneId.of("UTC")));
        return voiceSession;
    }

    private VoiceSessionVo processingSession() {
        VoiceSessionVo voiceSession = activeSession();
        voiceSession.setStatus(VoiceSessionStatus.PROCESSING.name());
        return voiceSession;
    }

    private VoiceInteractionCardVo focusedRecipientCard() {
        VoiceInteractionCardVo card = new VoiceInteractionCardVo();
        card.setSessionId(SESSION_ID);
        card.setCardId("30000000-0000-0000-0000-000000000001");
        card.setCardVersion(1);
        card.setSourceTurnId("40000000-0000-0000-0000-000000000001");
        card.setCardType("RECIPIENT_CANDIDATES");
        card.setActions("[\"SELECT_RECIPIENT\",\"ACCEPT_FOCUSED_SELECTION\"]");
        card.setFocusedItemId("50000000-0000-0000-0000-000000000001");
        card.setCandidateItems("[{\"id\":\"50000000-0000-0000-0000-000000000001\",\"displayName\":\"김철수\"}]");
        card.setActive(true);
        return card;
    }

    private VoiceInteractionCardVo amountCard(String focusedItemId, long amount) {
        VoiceInteractionCardVo card = new VoiceInteractionCardVo();
        card.setSessionId(SESSION_ID);
        card.setCardId("30000000-0000-0000-0000-000000000001");
        card.setCardVersion(1);
        card.setSourceTurnId("40000000-0000-0000-0000-000000000001");
        card.setCardType("AMOUNT_RECONFIRM");
        card.setActions("[\"SELECT_AMOUNT\",\"ACCEPT_FOCUSED_SELECTION\"]");
        card.setCandidateItems("[{\"id\":\"amount-a\",\"amount\":" + amount + "}]");
        card.setFocusedItemId(focusedItemId);
        card.setConfirmedRecipientId("50000000-0000-0000-0000-000000000001");
        card.setActive(true);
        return card;
    }

    private VoiceSessionVo continuationSession() {
        VoiceSessionVo voiceSession = activeSession();
        voiceSession.setCurrentStep(DialogueStep.AWAITING_CONTINUATION.name());
        return voiceSession;
    }

    private VoiceSessionVo processingContinuationSession() {
        VoiceSessionVo voiceSession = continuationSession();
        voiceSession.setStatus(VoiceSessionStatus.PROCESSING.name());
        return voiceSession;
    }

    private VoiceSessionVo backendTransferSession() {
        VoiceSessionVo voiceSession = activeSession();
        voiceSession.setFlowType(VoiceFlowType.TRANSFER.name());
        voiceSession.setSttMode("BACKEND_STREAM");
        return voiceSession;
    }

    private VoiceSessionVo processingBackendTransferSession() {
        VoiceSessionVo voiceSession = backendTransferSession();
        voiceSession.setStatus(VoiceSessionStatus.PROCESSING.name());
        return voiceSession;
    }

    private VoiceTransferOrchestrator testOrchestrator() {
        return new VoiceTransferOrchestratorImpl(
                new RecipientServiceRecipientCandidateAdapter(recipientService),
                new TransferServiceAmountValidationAdapter(transferService),
                new TransferServicePrepareAdapter(transferService),
                new TransferServiceRiskAssessmentAdapter(riskScoreService),
                new TransferServiceReadAdapter(transferService),
                new TransferServiceCancellationAdapter(transferService),
                new TransferServiceConfirmAdapter(transferService),
                new VoiceSessionMapperTransferLinkAdapter(voiceSessionMapper));
    }

    private VoiceSessionVo finalApprovalSession() {
        VoiceSessionVo voiceSession = backendTransferSession();
        voiceSession.setCurrentStep(DialogueStep.WAITING_FINAL_APPROVAL.name());
        voiceSession.setTransferId("70000000-0000-0000-0000-000000000001");
        return voiceSession;
    }

    private VoiceSessionVo processingFinalApprovalSession() {
        VoiceSessionVo voiceSession = finalApprovalSession();
        voiceSession.setStatus(VoiceSessionStatus.PROCESSING.name());
        return voiceSession;
    }

    private VoiceSessionVo riskCheckSession() {
        VoiceSessionVo voiceSession = backendTransferSession();
        voiceSession.setCurrentStep(DialogueStep.RISK_CHECK.name());
        voiceSession.setTransferId("70000000-0000-0000-0000-000000000001");
        return voiceSession;
    }

    private VoiceSessionVo processingRiskCheckSession() {
        VoiceSessionVo voiceSession = riskCheckSession();
        voiceSession.setStatus(VoiceSessionStatus.PROCESSING.name());
        return voiceSession;
    }

    private VoiceInteractionCardVo transferReadbackCard() {
        VoiceInteractionCardVo card = new VoiceInteractionCardVo();
        card.setSessionId(SESSION_ID);
        card.setCardId("30000000-0000-0000-0000-000000000001");
        card.setCardVersion(1);
        card.setSourceTurnId("40000000-0000-0000-0000-000000000001");
        card.setCardType("TRANSFER_READBACK");
        card.setActions("[]");
        card.setCandidateItems("[]");
        card.setActive(true);
        return card;
    }

    private VoiceInteractionCardVo transferRiskCheckCard() {
        VoiceInteractionCardVo card = transferReadbackCard();
        card.setCardType("TRANSFER_RISK_CHECK");
        return card;
    }

    private VoiceInteractionCardVo twoCandidateRecipientCard() {
        VoiceInteractionCardVo card = focusedRecipientCard();
        card.setCandidateItems("[{\"id\":\"50000000-0000-0000-0000-000000000001\",\"displayName\":\"김철수\"},"
                + "{\"id\":\"50000000-0000-0000-0000-000000000002\",\"displayName\":\"김영희\"}]");
        return card;
    }

    private TransferResponse canonicalReadback() {
        return new TransferResponse(
                java.util.UUID.fromString("70000000-0000-0000-0000-000000000001"), "DRAFT", "RISK_CHECK",
                java.util.UUID.fromString("60000000-0000-0000-0000-000000000001"),
                new TransferRecipientResponse(
                        java.util.UUID.fromString("50000000-0000-0000-0000-000000000001"),
                        "김철수", "004", "***-***-1234"),
                50_000L, null, List.of(), false,
                "김철수 님에게 50,000원을 보내시겠어요?", null, null, null);
    }

    private AzureSpeechDetailedResult azureResult() {
        return new AzureSpeechDetailedResult("오만 원 보내줘", new BigDecimal("0.95"), List.of());
    }

    private VoiceTurnAnalysisResult analysis() {
        JsonNode draftSummary = objectMapper.valueToTree(Map.of("recipient", "김철수"));
        return new VoiceTurnAnalysisResult(
                DialogueStep.AWAITING_AMOUNT,
                VoiceIntent.TRANSFER,
                Map.of("recipient", "김철수"),
                new BigDecimal("0.95"),
                "김철수 님에게 보낼 금액을 말씀해 주세요.",
                "<speak>김철수 님에게 보낼 금액을 말씀해 주세요.</speak>",
                objectMapper.valueToTree(Map.of("recipient", "김철수")),
                objectMapper.valueToTree(Map.of("name", "amount")),
                draftSummary,
                VoiceNextAction.ASK_AMOUNT,
                VoiceRequestedFunction.TRANSFER_RECIPIENT_CANDIDATES);
    }

    private VoiceTurnAnalysisResult accountInquiryAnalysis() {
        return new VoiceTurnAnalysisResult(
                DialogueStep.AWAITING_INPUT,
                VoiceIntent.ACCOUNT_INQUIRY,
                Map.of(),
                new BigDecimal("0.95"),
                "계좌를 확인해 드릴게요.",
                "<speak>계좌를 확인해 드릴게요.</speak>",
                null,
                null,
                null,
                VoiceNextAction.PRESENT_RESULT,
                VoiceRequestedFunction.ACCOUNT_INQUIRY);
    }

    private VoiceTurnAnalysisResult analysisWithUntrustedAmount() {
        return new VoiceTurnAnalysisResult(
                DialogueStep.AWAITING_RECIPIENT,
                VoiceIntent.TRANSFER,
                Map.of("amount", 900_000L),
                new BigDecimal("0.95"),
                "받는 분의 이름을 말씀해 주세요.",
                "<speak>받는 분의 이름을 말씀해 주세요.</speak>",
                objectMapper.valueToTree(Map.of("amount", 900_000L, "amountCandidates", List.of(900_000L))),
                objectMapper.valueToTree(Map.of("name", "recipient")),
                objectMapper.valueToTree(Map.of("amount", 900_000L)),
                VoiceNextAction.ASK_RECIPIENT,
                VoiceRequestedFunction.NONE);
    }

    private VoiceTurnAnalysisResult analysisWithRecipientAndUntrustedAmount() {
        return new VoiceTurnAnalysisResult(
                DialogueStep.AWAITING_RECIPIENT,
                VoiceIntent.TRANSFER,
                Map.of("recipient", "김철수", "amount", 900_000L),
                new BigDecimal("0.95"),
                "받는 분을 골라 주세요.",
                "<speak>받는 분을 골라 주세요.</speak>",
                objectMapper.valueToTree(Map.of()),
                objectMapper.valueToTree(Map.of("name", "recipientConfirmation")),
                objectMapper.valueToTree(Map.of()),
                VoiceNextAction.ASK_RECIPIENT,
                VoiceRequestedFunction.TRANSFER_RECIPIENT_CANDIDATES);
    }

    private DialogueTurnVo userTurn() {
        DialogueTurnVo dialogueTurn = new DialogueTurnVo();
        dialogueTurn.setTurnId(TURN_ID);
        dialogueTurn.setSessionId(SESSION_ID);
        dialogueTurn.setSequenceNo(2);
        dialogueTurn.setSpeaker("USER");
        dialogueTurn.setTranscript("김철수에게 오만원 보내줘");
        dialogueTurn.setSttConfidence(new BigDecimal("0.95"));
        dialogueTurn.setInputType(DialogueInputType.VOICE.name());
        return dialogueTurn;
    }

    private DialogueTurnVo aiTurn() throws Exception {
        VoiceTurnAnalysisResult result = analysis();
        DialogueTurnVo dialogueTurn = new DialogueTurnVo();
        dialogueTurn.setSessionId(SESSION_ID);
        dialogueTurn.setSequenceNo(3);
        dialogueTurn.setSpeaker("AI");
        dialogueTurn.setStep(result.getNextStep().name());
        dialogueTurn.setIntent(result.getIntent().name());
        dialogueTurn.setTtsText(result.getTtsText());
        dialogueTurn.setTtsSsml(result.getTtsSsml());
        dialogueTurn.setDisplayCard(objectMapper.writeValueAsString(result.getDisplayCard()));
        dialogueTurn.setExtractedSlots(objectMapper.writeValueAsString(Map.of(
                "slots", result.getSlots(),
                "confidence", result.getConfidence(),
                "nextAction", result.getNextAction().name(),
                "requestedFunction", result.getRequestedFunction().name(),
                "requiredSlot", result.getRequiredSlot(),
                "draftSummary", result.getDraftSummary())));
        return dialogueTurn;
    }

    private DialogueTurnVo continuationPrompt() {
        DialogueTurnVo dialogueTurn = new DialogueTurnVo();
        dialogueTurn.setTurnId("30000000-0000-0000-0000-000000000001");
        dialogueTurn.setSessionId(SESSION_ID);
        dialogueTurn.setSequenceNo(5);
        dialogueTurn.setSpeaker("AI");
        dialogueTurn.setStep(DialogueStep.AWAITING_CONTINUATION.name());
        return dialogueTurn;
    }

    private DialogueTurnVo clarifiedContinuationPrompt() {
        DialogueTurnVo dialogueTurn = continuationPrompt();
        dialogueTurn.setSequenceNo(7);
        return dialogueTurn;
    }
}
