package com.silvertown.domain.transfer.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TransferPrepareResponse {
    private final UUID transferId;
    private final String status;
    private final String currentStep;
    private final TransferRecipientResponse recipient;
    private final long amount;
    private final boolean amountReconfirmRequired;
    private final String confirmationText;
    private final OffsetDateTime preparedAt;
}
