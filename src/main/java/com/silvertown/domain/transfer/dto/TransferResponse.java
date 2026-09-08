package com.silvertown.domain.transfer.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TransferResponse {
    private final UUID transferId;
    private final String status;
    private final String currentStep;
    private final UUID fromAccountId;
    private final TransferRecipientResponse recipient;
    private final long amount;
    private final Long recognizedAmount;
    private final List<Long> amountCandidates;
    private final boolean amountReconfirmRequired;
    private final String confirmationText;
    private final OffsetDateTime preparedAt;
    private final OffsetDateTime approvedAt;
    private final OffsetDateTime executedAt;
}
