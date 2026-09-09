package com.silvertown.domain.transfer.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TransferConfirmResponse {
    private final UUID transferId;
    private final String status;
    private final String currentStep;
    private final boolean confirmed;
    private final boolean executable;
    private final String confirmationToken;
    private final OffsetDateTime confirmationTokenExpiresAt;
    private final OffsetDateTime approvedAt;
}
