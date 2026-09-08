package com.silvertown.domain.transfer.vo;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TransferAuthentication {
    private String transferAuthenticationId;
    private String transferId;
    private String userId;
    private String status;
    private OffsetDateTime expiresAt;
    private OffsetDateTime consumedAt;
    private OffsetDateTime authenticatedAt;
}
