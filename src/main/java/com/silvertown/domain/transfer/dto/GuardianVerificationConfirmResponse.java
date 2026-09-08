package com.silvertown.domain.transfer.dto;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GuardianVerificationConfirmResponse {
    private final boolean verified;
    private final String transferStatus;
    private final OffsetDateTime verifiedAt;
}
