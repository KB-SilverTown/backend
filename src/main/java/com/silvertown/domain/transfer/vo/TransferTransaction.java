package com.silvertown.domain.transfer.vo;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TransferTransaction {
    private String transactionId;
    private String transferId;
    private String userId;
    private String fromAccountId;
    private String recipientId;
    private long amount;
    private String status;
    private String idempotencyKey;
    private OffsetDateTime transferredAt;
}
