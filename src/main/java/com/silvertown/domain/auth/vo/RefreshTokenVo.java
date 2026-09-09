package com.silvertown.domain.auth.vo;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RefreshTokenVo {

  private String refreshTokenId;
  private String userId;
  private String tokenHash;
  private OffsetDateTime expiresAt;
  private OffsetDateTime revokedAt;
  private String revokedReason;
  private String replacedByTokenId;
  private OffsetDateTime lastUsedAt;
  private OffsetDateTime createdAt;
}
