package com.silvertown.domain.transfer.dto;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TransferAuthenticationResponse {
    private final boolean authenticated;
    private final OffsetDateTime expiresAt;
}
