package com.silvertown.domain.transfer.vo;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Transfer {
    private String transferId;
    private String userId;
    private String sessionId;
    private String fromAccountId;
    private String recipientId;
    private long amount;
    private String status;
    private String currentStep;
    private Long recognizedAmount;
    private String amountCandidates;
    private boolean amountReconfirmRequired;
    private String confirmationTokenHash;
    private OffsetDateTime confirmationTokenExpiresAt;
    private OffsetDateTime preparedAt;
    private OffsetDateTime approvedAt;
    private OffsetDateTime executedAt;
    private String recipientDisplayName;
    private String recipientBankCode;
    private byte[] recipientAccountNumberEncrypted;
}
