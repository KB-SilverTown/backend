package com.silvertown.domain.transfer.vo;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GuardianVerification {
    private String verificationId;
    private String transferId;
    private String targetPhoneHash;
    private String channel;
    private String status;
    private String verificationCodeHash;
    private int attemptCount;
    private int maxAttempts;
    private OffsetDateTime expiresAt;
    private OffsetDateTime sentAt;
    private OffsetDateTime verifiedAt;
    private OffsetDateTime failedAt;
}
