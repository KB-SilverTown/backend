package com.silvertown.domain.voice.service;

import com.silvertown.domain.recipient.dto.RecipientCandidateResponse;
import com.silvertown.domain.transfer.dto.AmountValidationRequest;
import com.silvertown.domain.transfer.dto.AmountValidationResponse;
import com.silvertown.domain.transfer.dto.TransferConfirmResponse;
import com.silvertown.domain.transfer.dto.TransferResponse;
import java.util.List;
import java.util.UUID;

/** Coordinates only verified voice-transfer commands; persistence remains in the transfer domain. */
public interface VoiceTransferOrchestrator {
    List<RecipientCandidateResponse> findRecipientCandidates(UUID userId, String keyword);

    AmountValidationResponse validateAmount(AmountValidationRequest request);

    VoiceTransferPreparation prepareAndAssess(
            UUID userId,
            UUID voiceSessionId,
            UUID fromAccountId,
            UUID recipientId,
            Long amount);

    VoiceTransferRiskCheck checkRisk(UUID userId, UUID transferId, String purposeAnswer);

    TransferResponse getTransfer(UUID userId, UUID transferId);

    void cancelUnexecuted(UUID userId, UUID transferId);

    TransferConfirmResponse confirm(UUID userId, UUID transferId, boolean approved);
}
