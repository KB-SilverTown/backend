package com.silvertown.domain.voice.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvertown.domain.risk.dto.RiskScoreResponse;
import com.silvertown.domain.risk.service.RiskScoreService;
import com.silvertown.domain.transfer.service.TransferService;
import com.silvertown.domain.transfer.dto.AmountValidationResponse;
import com.silvertown.domain.transfer.dto.TransferPrepareResponse;
import com.silvertown.domain.transfer.dto.TransferRecipientResponse;
import com.silvertown.domain.transfer.dto.TransferResponse;
import com.silvertown.domain.voice.dto.VoiceUiActionRequest;
import com.silvertown.domain.voice.dto.VoiceUiActionResponse;
import com.silvertown.domain.voice.mapper.DialogueTurnMapper;
import com.silvertown.domain.voice.mapper.VoiceInteractionCardMapper;
import com.silvertown.domain.voice.mapper.VoiceSessionMapper;
import com.silvertown.domain.voice.mapper.VoiceUiActionMapper;
import com.silvertown.domain.voice.service.VoiceSsmlRenderer;
import com.silvertown.domain.voice.service.VoiceTransferOrchestrator;
import com.silvertown.domain.voice.vo.DialogueTurnVo;
import com.silvertown.domain.voice.vo.VoiceInteractionCardVo;
import com.silvertown.domain.voice.vo.VoiceSessionVo;
import com.silvertown.domain.voice.vo.VoiceUiActionVo;
import com.silvertown.global.common.exception.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class VoiceUiActionServiceImplTest {
    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String SESSION_ID = "10000000-0000-0000-0000-000000000001";
    private static final String SOURCE_TURN_ID = "20000000-0000-0000-0000-000000000001";
    private static final String CARD_ID = "30000000-0000-0000-0000-000000000001";
    private static final String ACTION_ID = "40000000-0000-0000-0000-000000000001";
    private static final String FIRST_RECIPIENT_ID = "50000000-0000-0000-0000-000000000001";
    private static final String SECOND_RECIPIENT_ID = "50000000-0000-0000-0000-000000000002";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-06T01:00:00Z"), ZoneOffset.UTC);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private VoiceSessionMapper voiceSessionMapper;
    private VoiceInteractionCardMapper voiceInteractionCardMapper;
    private VoiceUiActionMapper voiceUiActionMapper;
    private DialogueTurnMapper dialogueTurnMapper;
    private TransferService transferService;
    private RiskScoreService riskScoreService;
    private VoiceUiActionServiceImpl service;

    @BeforeEach
    void setUp() {
        voiceSessionMapper = org.mockito.Mockito.mock(VoiceSessionMapper.class);
        voiceInteractionCardMapper = org.mockito.Mockito.mock(VoiceInteractionCardMapper.class);
        voiceUiActionMapper = org.mockito.Mockito.mock(VoiceUiActionMapper.class);
        dialogueTurnMapper = org.mockito.Mockito.mock(DialogueTurnMapper.class);
        transferService = org.mockito.Mockito.mock(TransferService.class);
        riskScoreService = org.mockito.Mockito.mock(RiskScoreService.class);
        when(voiceInteractionCardMapper.replace(any(VoiceInteractionCardVo.class))).thenReturn(1);
        service = new VoiceUiActionServiceImpl(
                voiceSessionMapper,
                voiceInteractionCardMapper,
                voiceUiActionMapper,
                dialogueTurnMapper,
                testOrchestrator(),
                org.mockito.Mockito.mock(VoiceSsmlRenderer.class),
                objectMapper,
                CLOCK);
    }

    @Test
    void selectingRecipientUpdatesOnlyFocusWithoutTts() throws Exception {
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(activeSession());
        when(voiceInteractionCardMapper.findBySessionIdForUpdate(SESSION_ID)).thenReturn(recipientCard());
        when(dialogueTurnMapper.findNextSequenceNo(SESSION_ID)).thenReturn(2);
        when(voiceSessionMapper.updateStatusAndStep(any(), any(), any(), any())).thenReturn(1);

        VoiceUiActionResponse response = service.process(USER_ID, SESSION_ID,
                request(ACTION_ID, SECOND_RECIPIENT_ID, 1));

        assertNull(response.getTtsText());
        assertNull(response.getTtsSsml());
        assertEquals("RECONFIRM_INPUT", response.getNextAction());
        assertEquals(2, response.getDisplayCard().path("cardVersion").asInt());
        assertEquals(SECOND_RECIPIENT_ID, response.getDisplayCard().path("focusedItemId").asText());
        assertEquals(true, response.getDisplayCard().path("items").get(1).path("isFocused").asBoolean());
        verify(dialogueTurnMapper).insert(any(DialogueTurnVo.class));
        verify(voiceInteractionCardMapper).replace(any(VoiceInteractionCardVo.class));
    }

    @Test
    void reusesSameActionIdInsteadOfCreatingAnotherAiTurn() throws Exception {
        AtomicReference<VoiceUiActionVo> savedAction = new AtomicReference<>();
        AtomicReference<DialogueTurnVo> savedTurn = new AtomicReference<>();
        when(voiceUiActionMapper.findByActionId(ACTION_ID)).thenAnswer(invocation -> savedAction.get());
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(activeSession());
        when(voiceInteractionCardMapper.findBySessionIdForUpdate(SESSION_ID)).thenReturn(recipientCard());
        when(dialogueTurnMapper.findNextSequenceNo(SESSION_ID)).thenReturn(2);
        when(voiceSessionMapper.updateStatusAndStep(any(), any(), any(), any())).thenReturn(1);
        when(voiceUiActionMapper.insert(any())).thenAnswer(invocation -> {
            savedAction.set(invocation.getArgument(0));
            return 1;
        });
        when(dialogueTurnMapper.insert(any())).thenAnswer(invocation -> {
            savedTurn.set(invocation.getArgument(0));
            return 1;
        });
        when(dialogueTurnMapper.findBySessionIdAndTurnId(eq(SESSION_ID), any()))
                .thenAnswer(invocation -> savedTurn.get());

        VoiceUiActionResponse first = service.process(USER_ID, SESSION_ID,
                request(ACTION_ID, SECOND_RECIPIENT_ID, 1));
        VoiceUiActionResponse retry = service.process(USER_ID, SESSION_ID,
                request(ACTION_ID, SECOND_RECIPIENT_ID, 1));

        assertEquals(first.getResponseTurnId(), retry.getResponseTurnId());
        verify(dialogueTurnMapper, times(1)).insert(any(DialogueTurnVo.class));
        verify(voiceUiActionMapper, times(1)).insert(any(VoiceUiActionVo.class));
    }

    @Test
    void rejectsStaleCardBeforeChangingFocus() throws Exception {
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(activeSession());
        when(voiceInteractionCardMapper.findBySessionIdForUpdate(SESSION_ID)).thenReturn(recipientCard());

        BusinessException exception = assertThrows(BusinessException.class, () -> service.process(
                USER_ID, SESSION_ID, request(ACTION_ID, SECOND_RECIPIENT_ID, 2)));

        assertEquals("VOICE_CARD_STALE", exception.getErrorCode().getCode());
    }

    @Test
    void acceptingFocusedRecipientStoresTheFocusedCandidateAsConfirmed() throws Exception {
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(activeSession());
        when(voiceInteractionCardMapper.findBySessionIdForUpdate(SESSION_ID)).thenReturn(recipientCard());
        when(dialogueTurnMapper.findNextSequenceNo(SESSION_ID)).thenReturn(2);
        when(voiceSessionMapper.updateStatusAndStep(any(), any(), any(), any())).thenReturn(1);

        VoiceUiActionResponse response = service.process(USER_ID, SESSION_ID, acceptRequest(ACTION_ID));

        ArgumentCaptor<VoiceInteractionCardVo> cardCaptor = ArgumentCaptor.forClass(VoiceInteractionCardVo.class);
        verify(voiceInteractionCardMapper).replace(cardCaptor.capture());
        assertEquals(FIRST_RECIPIENT_ID, cardCaptor.getValue().getConfirmedRecipientId());
        assertEquals(false, cardCaptor.getValue().isActive());
        assertEquals("보낼 금액을 말씀해 주세요.", response.getTtsText());
        assertEquals("ASK_AMOUNT", response.getNextAction());
    }

    @Test
    void reasksForAmountWithoutPreparingTransferWhenAmountValidationRequiresReconfirmation() throws Exception {
        VoiceSessionVo session = activeSession();
        session.setFromAccountId("60000000-0000-0000-0000-000000000001");
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(session);
        when(voiceInteractionCardMapper.findBySessionIdForUpdate(SESSION_ID)).thenReturn(amountCard());
        when(dialogueTurnMapper.findNextSequenceNo(SESSION_ID)).thenReturn(2);
        when(voiceSessionMapper.updateStatusAndStep(any(), any(), any(), any())).thenReturn(1);
        when(transferService.validateAmount(any())).thenReturn(new AmountValidationResponse(
                null, java.util.List.of(70_000L), null, true));

        VoiceUiActionResponse response = service.process(USER_ID, SESSION_ID, acceptRequest(ACTION_ID));

        assertEquals(com.silvertown.domain.voice.enums.DialogueStep.RECONFIRMING, response.getState());
        assertEquals("RECONFIRM_INPUT", response.getNextAction());
        assertEquals(1, response.getDisplayCard().path("items").size());
        assertEquals(70_000L, response.getDisplayCard().path("items").get(0).path("amount").longValue());
        assertEquals(response.getDisplayCard().path("items").get(0).path("id").asText(),
                response.getDisplayCard().path("focusedItemId").asText());
        assertEquals("보낼 금액은 70,000원이 맞을까요?", response.getTtsText());
        verify(transferService, never()).prepare(any(), any());
    }

    @Test
    void requestsFinalApprovalAfterSafeRiskInsteadOfRequestingPin() throws Exception {
        VoiceSessionVo session = activeSession();
        session.setFromAccountId("60000000-0000-0000-0000-000000000001");
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(session);
        when(voiceInteractionCardMapper.findBySessionIdForUpdate(SESSION_ID)).thenReturn(amountCard());
        when(dialogueTurnMapper.findNextSequenceNo(SESSION_ID)).thenReturn(2);
        when(voiceSessionMapper.updateStatusAndStep(any(), any(), any(), any())).thenReturn(1);
        when(voiceSessionMapper.updateTransferId(any(), any(), any())).thenReturn(1);
        when(transferService.validateAmount(any())).thenReturn(new AmountValidationResponse(
                50_000L, java.util.List.of(50_000L), 50_000L, false));
        when(transferService.prepare(any(), any())).thenReturn(new TransferPrepareResponse(
                java.util.UUID.fromString("70000000-0000-0000-0000-000000000001"), "DRAFT", "RISK_CHECK",
                new TransferRecipientResponse(java.util.UUID.fromString(FIRST_RECIPIENT_ID),
                        "김철수", "004", "***-***-1234"),
                50_000L, false, "김철수 님에게 50,000원을 보내시겠어요?", null));
        when(riskScoreService.assess(any(), any())).thenReturn(new RiskScoreResponse(
                0, 0, "LOW", "RULE", false, java.util.List.of(), null, "ALLOW", false));
        when(transferService.get(any(), any())).thenReturn(new TransferResponse(
                java.util.UUID.fromString("70000000-0000-0000-0000-000000000001"), "DRAFT", "RISK_CHECK",
                java.util.UUID.fromString("60000000-0000-0000-0000-000000000001"),
                new TransferRecipientResponse(java.util.UUID.fromString(FIRST_RECIPIENT_ID),
                        "김철수", "004", "***-***-1234"),
                50_000L, null, java.util.List.of(), false,
                "김철수 님에게 50,000원을 보내시겠어요?", null, null, null));

        VoiceUiActionResponse response = service.process(USER_ID, SESSION_ID, acceptRequest(ACTION_ID));

        assertEquals(com.silvertown.domain.voice.enums.DialogueStep.WAITING_FINAL_APPROVAL, response.getState());
        assertEquals("ASK_FINAL_APPROVAL", response.getNextAction());
        assertEquals("김철수 님에게 50,000원을 보내시겠어요?", response.getTtsText());
        assertEquals("70000000-0000-0000-0000-000000000001", response.getDisplayCard().path("transferId").asText());
        assertEquals("김철수", response.getDisplayCard().path("recipientName").asText());
        assertEquals(50_000L, response.getDisplayCard().path("amount").longValue());
        verify(transferService, never()).confirm(any(), any(), any());
    }

    @Test
    void holdsRiskyTransferAndDoesNotRequestApprovalOrPin() throws Exception {
        VoiceSessionVo session = activeSession();
        session.setFromAccountId("60000000-0000-0000-0000-000000000001");
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(session);
        when(voiceInteractionCardMapper.findBySessionIdForUpdate(SESSION_ID)).thenReturn(amountCard());
        when(dialogueTurnMapper.findNextSequenceNo(SESSION_ID)).thenReturn(2);
        when(voiceSessionMapper.updateStatusAndStep(any(), any(), any(), any())).thenReturn(1);
        when(voiceSessionMapper.updateTransferId(any(), any(), any())).thenReturn(1);
        when(transferService.validateAmount(any())).thenReturn(new AmountValidationResponse(
                50_000L, java.util.List.of(50_000L), 50_000L, false));
        when(transferService.prepare(any(), any())).thenReturn(new TransferPrepareResponse(
                java.util.UUID.fromString("70000000-0000-0000-0000-000000000001"), "DRAFT", "RISK_CHECK",
                new TransferRecipientResponse(java.util.UUID.fromString(FIRST_RECIPIENT_ID),
                        "김철수", "004", "***-***-1234"),
                50_000L, false, "김철수 님에게 50,000원을 보내시겠어요?", null));
        when(riskScoreService.assess(any(), any())).thenReturn(new RiskScoreResponse(
                60, 60, "HIGH", "RULE", true, java.util.List.of(), null, "HOLD", true));
        when(transferService.get(any(), any())).thenReturn(new TransferResponse(
                java.util.UUID.fromString("70000000-0000-0000-0000-000000000001"), "HELD", "RISK_CHECK",
                java.util.UUID.fromString("60000000-0000-0000-0000-000000000001"),
                new TransferRecipientResponse(java.util.UUID.fromString(FIRST_RECIPIENT_ID),
                        "김철수", "004", "***-***-1234"),
                50_000L, null, java.util.List.of(), false,
                "김철수 님에게 50,000원을 보내시겠어요?", null, null, null));

        VoiceUiActionResponse response = service.process(USER_ID, SESSION_ID, acceptRequest(ACTION_ID));

        assertEquals(com.silvertown.domain.voice.enums.DialogueStep.HELD, response.getState());
        assertEquals("WAIT_GUARDIAN_VERIFICATION", response.getNextAction());
        assertEquals("TRANSFER_HELD", response.getDisplayCard().path("type").asText());
        assertEquals("70000000-0000-0000-0000-000000000001", response.getDisplayCard().path("transferId").asText());
        verify(transferService, never()).confirm(any(), any(), any());
    }

    @Test
    void rejectsUiActionWhileTurnProcessingOwnsTheSession() throws Exception {
        VoiceSessionVo processing = activeSession();
        processing.setStatus("PROCESSING");
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(processing);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.process(
                USER_ID, SESSION_ID, request(ACTION_ID, SECOND_RECIPIENT_ID, 1)));

        assertEquals("VOICE_TURN_CONFLICT", exception.getErrorCode().getCode());
    }

    @Test
    void rejectsDifferentPayloadForAlreadyRecordedActionId() throws Exception {
        VoiceUiActionVo existing = new VoiceUiActionVo();
        existing.setActionId(ACTION_ID);
        existing.setSessionId(SESSION_ID);
        existing.setRequestHash("another-request-hash");
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(activeSession());
        when(voiceUiActionMapper.findByActionId(ACTION_ID)).thenReturn(existing);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.process(
                USER_ID, SESSION_ID, request(ACTION_ID, SECOND_RECIPIENT_ID, 1)));

        assertEquals("VOICE_UI_ACTION_CONFLICT", exception.getErrorCode().getCode());
    }

    @Test
    void rejectsActionReplayBeforeLookingUpActionWhenSessionIsNotOwned() throws Exception {
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.process(
                USER_ID, SESSION_ID, request(ACTION_ID, SECOND_RECIPIENT_ID, 1)));

        assertEquals("VOICE_SESSION_NOT_FOUND", exception.getErrorCode().getCode());
        verify(voiceUiActionMapper, never()).findByActionId(ACTION_ID);
    }

    @Test
    void rejectsMalformedStoredCandidateItemsWithoutClassCastException() throws Exception {
        assertMalformedCandidateItems("[\"invalid\"]");
        assertMalformedCandidateItems("[null]");
        assertMalformedCandidateItems("");
        assertMalformedCandidateItems(null);
    }

    private VoiceUiActionRequest request(String actionId, String itemId, int cardVersion) throws Exception {
        return objectMapper.readValue("""
                {"actionId":"%s","sourceTurnId":"%s","cardId":"%s","cardVersion":%d,
                 "actionType":"SELECT_RECIPIENT","itemId":"%s"}
                """.formatted(actionId, SOURCE_TURN_ID, CARD_ID, cardVersion, itemId), VoiceUiActionRequest.class);
    }

    private VoiceTransferOrchestrator testOrchestrator() {
        return new VoiceTransferOrchestratorImpl(
                (userId, keyword) -> java.util.List.of(),
                new TransferServiceAmountValidationAdapter(transferService),
                new TransferServicePrepareAdapter(transferService),
                new TransferServiceRiskAssessmentAdapter(riskScoreService),
                new TransferServiceReadAdapter(transferService),
                new TransferServiceCancellationAdapter(transferService),
                new TransferServiceConfirmAdapter(transferService),
                new VoiceSessionMapperTransferLinkAdapter(voiceSessionMapper));
    }

    private VoiceUiActionRequest acceptRequest(String actionId) throws Exception {
        return objectMapper.readValue("""
                {"actionId":"%s","sourceTurnId":"%s","cardId":"%s","cardVersion":1,
                 "actionType":"ACCEPT_FOCUSED_SELECTION"}
                """.formatted(actionId, SOURCE_TURN_ID, CARD_ID), VoiceUiActionRequest.class);
    }

    private VoiceUiActionRequest rejectRequest(String actionId) throws Exception {
        return objectMapper.readValue("""
                {"actionId":"%s","sourceTurnId":"%s","cardId":"%s","cardVersion":1,
                 "actionType":"REJECT_FOCUSED_SELECTION"}
                """.formatted(actionId, SOURCE_TURN_ID, CARD_ID), VoiceUiActionRequest.class);
    }

    private void assertMalformedCandidateItems(String candidateItems) throws Exception {
        VoiceInteractionCardVo malformed = recipientCard();
        malformed.setCandidateItems(candidateItems);
        when(voiceSessionMapper.findOwnedByIdForUpdate(USER_ID, SESSION_ID)).thenReturn(activeSession());
        when(voiceInteractionCardMapper.findBySessionIdForUpdate(SESSION_ID)).thenReturn(malformed);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.process(USER_ID, SESSION_ID, rejectRequest(ACTION_ID)));

        assertEquals("INTERNAL_SERVER_ERROR", exception.getErrorCode().getCode());
    }

    private VoiceSessionVo activeSession() {
        VoiceSessionVo session = new VoiceSessionVo();
        session.setSessionId(SESSION_ID);
        session.setUserId(USER_ID);
        session.setStatus("LISTENING");
        session.setCurrentStep("AWAITING_RECIPIENT");
        session.setExpiresAt(LocalDateTime.of(2026, 9, 6, 11, 0));
        return session;
    }

    private VoiceInteractionCardVo recipientCard() {
        VoiceInteractionCardVo card = new VoiceInteractionCardVo();
        card.setSessionId(SESSION_ID);
        card.setCardId(CARD_ID);
        card.setCardVersion(1);
        card.setSourceTurnId(SOURCE_TURN_ID);
        card.setCardType("RECIPIENT_CANDIDATES");
        card.setActions("[\"SELECT_RECIPIENT\",\"ACCEPT_FOCUSED_SELECTION\",\"REJECT_FOCUSED_SELECTION\",\"CANCEL_FLOW\"]");
        card.setCandidateItems("""
                [{"id":"50000000-0000-0000-0000-000000000001","displayName":"김철수"},
                 {"id":"50000000-0000-0000-0000-000000000002","displayName":"김영희"}]
                """);
        card.setFocusedItemId(FIRST_RECIPIENT_ID);
        card.setActive(true);
        return card;
    }

    private VoiceInteractionCardVo amountCard() {
        VoiceInteractionCardVo card = recipientCard();
        card.setCardType("AMOUNT_RECONFIRM");
        card.setActions("[\"SELECT_AMOUNT\",\"ACCEPT_FOCUSED_SELECTION\",\"CANCEL_FLOW\"]");
        card.setCandidateItems("[{\"id\":\"amount-1\",\"amount\":50000}]");
        card.setFocusedItemId("amount-1");
        card.setConfirmedRecipientId(FIRST_RECIPIENT_ID);
        return card;
    }
}
