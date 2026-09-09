package com.silvertown.domain.voice.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.silvertown.domain.risk.dto.RiskScoreResponse;
import com.silvertown.domain.transfer.dto.AmountValidationResponse;
import com.silvertown.domain.transfer.dto.TransferPrepareResponse;
import com.silvertown.domain.transfer.dto.TransferRecipientResponse;
import com.silvertown.domain.transfer.dto.TransferResponse;
import com.silvertown.domain.voice.service.AmountValidationPort;
import com.silvertown.domain.voice.service.RecipientCandidatePort;
import com.silvertown.domain.voice.service.RiskAssessmentPort;
import com.silvertown.domain.voice.service.TransferConfirmPort;
import com.silvertown.domain.voice.service.TransferCancellationPort;
import com.silvertown.domain.voice.service.TransferPreparePort;
import com.silvertown.domain.voice.service.TransferReadPort;
import com.silvertown.domain.voice.service.VoiceSessionTransferLinkPort;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VoiceTransferOrchestratorImplTest {
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SESSION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ACCOUNT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID RECIPIENT_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID TRANSFER_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");

    private AmountValidationPort amountValidationPort;
    private TransferPreparePort transferPreparePort;
    private RiskAssessmentPort riskAssessmentPort;
    private TransferReadPort transferReadPort;
    private TransferCancellationPort transferCancellationPort;
    private VoiceSessionTransferLinkPort voiceSessionTransferLinkPort;
    private VoiceTransferOrchestratorImpl orchestrator;

    @BeforeEach
    void setUp() {
        amountValidationPort = org.mockito.Mockito.mock(AmountValidationPort.class);
        transferPreparePort = org.mockito.Mockito.mock(TransferPreparePort.class);
        riskAssessmentPort = org.mockito.Mockito.mock(RiskAssessmentPort.class);
        transferReadPort = org.mockito.Mockito.mock(TransferReadPort.class);
        transferCancellationPort = org.mockito.Mockito.mock(TransferCancellationPort.class);
        voiceSessionTransferLinkPort = org.mockito.Mockito.mock(VoiceSessionTransferLinkPort.class);
        orchestrator = new VoiceTransferOrchestratorImpl(
                org.mockito.Mockito.mock(RecipientCandidatePort.class),
                amountValidationPort,
                transferPreparePort,
                riskAssessmentPort,
                transferReadPort,
                transferCancellationPort,
                org.mockito.Mockito.mock(TransferConfirmPort.class),
                voiceSessionTransferLinkPort);
    }

    @Test
    void blocksDraftAndRiskWhenAmountStillRequiresReconfirmation() {
        when(amountValidationPort.validate(any())).thenReturn(new AmountValidationResponse(
                null, List.of(50_000L, 70_000L), null, true));

        var result = orchestrator.prepareAndAssess(USER_ID, SESSION_ID, ACCOUNT_ID, RECIPIENT_ID, 50_000L);

        assertNull(result.transfer());
        assertNull(result.risk());
        assertNull(result.readback());
        verify(transferPreparePort, never()).prepare(any(), any());
        verify(riskAssessmentPort, never()).assess(any(), any());
        verify(transferReadPort, never()).get(any(), any());
    }

    @Test
    void blocksDraftAndRiskWhenCanonicalAmountDiffersFromRequestedAmount() {
        when(amountValidationPort.validate(any())).thenReturn(new AmountValidationResponse(
                70_000L, List.of(50_000L), 70_000L, false));

        var result = orchestrator.prepareAndAssess(USER_ID, SESSION_ID, ACCOUNT_ID, RECIPIENT_ID, 50_000L);

        assertNull(result.transfer());
        assertNull(result.risk());
        assertNull(result.readback());
        verify(transferPreparePort, never()).prepare(any(), any());
        verify(riskAssessmentPort, never()).assess(any(), any());
        verify(transferReadPort, never()).get(any(), any());
    }

    @Test
    void readsCanonicalTransferValuesAfterPreparingAndAssessingRisk() {
        TransferPrepareResponse prepared = new TransferPrepareResponse(
                TRANSFER_ID, "DRAFT", "RISK_CHECK",
                new TransferRecipientResponse(RECIPIENT_ID, "김철수", "004", "***-***-1234"),
                50_000L, false, "준비 응답", null);
        TransferResponse canonical = new TransferResponse(
                TRANSFER_ID, "DRAFT", "RISK_CHECK", ACCOUNT_ID,
                new TransferRecipientResponse(RECIPIENT_ID, "김철수", "004", "***-***-1234"),
                50_000L, null, List.of(), false, "서버 Read-back", null, null, null);
        when(amountValidationPort.validate(any())).thenReturn(new AmountValidationResponse(
                50_000L, List.of(50_000L), 50_000L, false));
        when(transferPreparePort.prepare(any(), any())).thenReturn(prepared);
        when(riskAssessmentPort.assess(USER_ID, TRANSFER_ID)).thenReturn(new RiskScoreResponse(
                0, 0, "LOW", "RULE", false, List.of(), null, "ALLOW", false));
        when(transferReadPort.get(USER_ID, TRANSFER_ID)).thenReturn(canonical);

        var result = orchestrator.prepareAndAssess(USER_ID, SESSION_ID, ACCOUNT_ID, RECIPIENT_ID, 50_000L);

        assertEquals(TRANSFER_ID, result.transfer().getTransferId());
        assertEquals("서버 Read-back", result.readback().getConfirmationText());
        verify(riskAssessmentPort).assess(USER_ID, TRANSFER_ID);
        verify(voiceSessionTransferLinkPort).link(USER_ID, SESSION_ID, TRANSFER_ID);
        verify(transferReadPort).get(USER_ID, TRANSFER_ID);
    }

    @Test
    void doesNotReadBackWhenTransferCannotBeLinkedToTheVoiceSession() {
        TransferPrepareResponse prepared = new TransferPrepareResponse(
                TRANSFER_ID, "DRAFT", "RISK_CHECK",
                new TransferRecipientResponse(RECIPIENT_ID, "김철수", "004", "***-***-1234"),
                50_000L, false, "준비 응답", null);
        when(amountValidationPort.validate(any())).thenReturn(new AmountValidationResponse(
                50_000L, List.of(50_000L), 50_000L, false));
        when(transferPreparePort.prepare(any(), any())).thenReturn(prepared);
        when(riskAssessmentPort.assess(USER_ID, TRANSFER_ID)).thenReturn(new RiskScoreResponse(
                80, 80, "HIGH", "RULE", true, List.of(), null, "HOLD", true));
        org.mockito.Mockito.doThrow(new RuntimeException("link failure"))
                .when(voiceSessionTransferLinkPort).link(USER_ID, SESSION_ID, TRANSFER_ID);

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> orchestrator.prepareAndAssess(USER_ID, SESSION_ID, ACCOUNT_ID, RECIPIENT_ID, 50_000L));

        verify(transferReadPort, never()).get(any(), any());
    }

    @Test
    void delegatesUnexecutedCancellationWithoutReadingTransferState() {
        orchestrator.cancelUnexecuted(USER_ID, TRANSFER_ID);

        verify(transferCancellationPort).cancel(USER_ID, TRANSFER_ID);
        verify(transferReadPort, never()).get(any(), any());
    }
}
