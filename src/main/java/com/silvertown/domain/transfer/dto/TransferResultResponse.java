package com.silvertown.domain.transfer.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TransferResultResponse {
    private final UUID transactionId;
    private final UUID transferId;
    private final String status;
    private final long amount;
    private final OffsetDateTime transferredAt;
}
