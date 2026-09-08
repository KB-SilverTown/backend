package com.silvertown.domain.auth.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AuthResponse {

  private final String accessToken;
  private final String refreshToken;
  private final OffsetDateTime expiresAt;
  private final UUID userId;
}
