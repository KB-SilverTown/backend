package com.silvertown.domain.voice.service.impl;

import com.silvertown.domain.recipient.dto.RecipientCandidateResponse;
import com.silvertown.domain.risk.dto.RiskCheckResponse;
import com.silvertown.domain.risk.dto.RiskScoreResponse;
import com.silvertown.domain.transfer.dto.AmountValidationRequest;
import com.silvertown.domain.transfer.dto.AmountValidationResponse;
import com.silvertown.domain.transfer.dto.TransferConfirmResponse;
import com.silvertown.domain.transfer.dto.TransferPrepareRequest;
import com.silvertown.domain.transfer.dto.TransferPrepareResponse;
import com.silvertown.domain.transfer.dto.TransferResponse;
import com.silvertown.domain.voice.service.AmountValidationPort;
import com.silvertown.domain.voice.service.RecipientCandidatePort;
import com.silvertown.domain.voice.service.RiskAssessmentPort;
import com.silvertown.domain.voice.service.TransferConfirmPort;
import com.silvertown.domain.voice.service.TransferCancellationPort;
import com.silvertown.domain.voice.service.TransferPreparePort;
import com.silvertown.domain.voice.service.TransferReadPort;
import com.silvertown.domain.voice.service.VoiceTransferOrchestrator;
import com.silvertown.domain.voice.service.VoiceTransferPreparation;
import com.silvertown.domain.voice.service.VoiceTransferRiskCheck;
import com.silvertown.domain.voice.service.VoiceSessionTransferLinkPort;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VoiceTransferOrchestratorImpl implements VoiceTransferOrchestrator {
    private final RecipientCandidatePort recipientCandidatePort;
    private final AmountValidationPort amountValidationPort;
    private final TransferPreparePort transferPreparePort;
    private final RiskAssessmentPort riskAssessmentPort;
    private final TransferReadPort transferReadPort;
    private final TransferCancellationPort transferCancellationPort;
    private final TransferConfirmPort transferConfirmPort;
    private final VoiceSessionTransferLinkPort voiceSessionTransferLinkPort;

    @Override
    public List<RecipientCandidateResponse> findRecipientCandidates(UUID userId, String keyword) {
        return recipientCandidatePort.findCandidates(userId, keyword);
    }

    @Override
    public AmountValidationResponse validateAmount(AmountValidationRequest request) {
        return amountValidationPort.validate(request);
    }

    @Override
    @Transactional
    public VoiceTransferPreparation prepareAndAssess(
            UUID userId,
            UUID voiceSessionId,
            UUID fromAccountId,
            UUID recipientId,
            Long amount) {
        AmountValidationResponse validation = validateAmount(
                AmountValidationRequest.of(amount, List.of(amount)));
        if (validation.isAmountReconfirmRequired()
                || validation.getConfirmedAmount() == null
                || !validation.getConfirmedAmount().equals(amount)) {
            return new VoiceTransferPreparation(validation, null, null, null);
        }
        TransferPrepareResponse transfer = transferPreparePort.prepare(
                userId,
                TransferPrepareRequest.of(fromAccountId, recipientId,
                        validation.getConfirmedAmount(), voiceSessionId));
        RiskScoreResponse risk = riskAssessmentPort.assess(userId, transfer.getTransferId());
        voiceSessionTransferLinkPort.link(userId, voiceSessionId, transfer.getTransferId());
        TransferResponse readback = transferReadPort.get(userId, transfer.getTransferId());
        return new VoiceTransferPreparation(validation, transfer, risk, readback);
    }

    @Override
    public VoiceTransferRiskCheck checkRisk(UUID userId, UUID transferId, String purposeAnswer) {
        RiskCheckResponse risk = riskAssessmentPort.checkContext(userId, transferId, purposeAnswer);
        return new VoiceTransferRiskCheck(risk, transferReadPort.get(userId, transferId));
    }

    @Override
    public TransferResponse getTransfer(UUID userId, UUID transferId) {
        return transferReadPort.get(userId, transferId);
    }

    @Override
    public void cancelUnexecuted(UUID userId, UUID transferId) {
        transferCancellationPort.cancel(userId, transferId);
    }

    @Override
    public TransferConfirmResponse confirm(UUID userId, UUID transferId, boolean approved) {
        return transferConfirmPort.confirm(userId, transferId, approved);
    }
}
