package com.silvertown.domain.transfer.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GuardianVerificationStartResponse {
    private final UUID verificationId;
    private final String status;
    private final OffsetDateTime expiresAt;
    private final String deliveryFailureCode;
}
