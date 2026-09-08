package com.silvertown.global.security;

import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

  private static final String ISSUER = "silvertown";
  private static final String TOKEN_TYPE_CLAIM = "tokenType";
  private static final String ACCESS_TOKEN_TYPE = "ACCESS";
  private static final String REFRESH_TOKEN_TYPE = "REFRESH";
  private static final int HMAC_KEY_LENGTH_BYTES = 32;

  private final Clock clock;
  private final Key signingKey;
  private final long accessTtlSeconds;
  private final long refreshTtlSeconds;

  public JwtTokenProvider(
      Clock clock,
      @Value("${jwt.secret:${JWT_SECRET:}}") String encodedSecret,
      @Value("${jwt.access-ttl-seconds:${JWT_ACCESS_TTL_SECONDS:3600}}") long accessTtlSeconds,
      @Value("${jwt.refresh-ttl-seconds:${JWT_REFRESH_TTL_SECONDS:1209600}}") long refreshTtlSeconds) {
    this.clock = clock;
    this.signingKey = createSigningKey(encodedSecret);
    this.accessTtlSeconds = accessTtlSeconds;
    this.refreshTtlSeconds = refreshTtlSeconds;
  }

  public IssuedTokenPair issue(UUID userId) {
    Instant now = clock.instant();
    Instant accessExpiresAt = now.plusSeconds(accessTtlSeconds);
    Instant refreshExpiresAt = now.plusSeconds(refreshTtlSeconds);
    return new IssuedTokenPair(
        createToken(userId, ACCESS_TOKEN_TYPE, now, accessExpiresAt),
        createToken(userId, REFRESH_TOKEN_TYPE, now, refreshExpiresAt),
        OffsetDateTime.ofInstant(accessExpiresAt, ZoneId.of("Asia/Seoul")),
        OffsetDateTime.ofInstant(refreshExpiresAt, ZoneId.of("Asia/Seoul")));
  }

  public UUID getRefreshTokenUserId(String refreshToken) {
    try {
      Jws<Claims> parsed = parser().parseClaimsJws(refreshToken);
      if (!REFRESH_TOKEN_TYPE.equals(parsed.getBody().get(TOKEN_TYPE_CLAIM, String.class))) {
        throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
      }
      return UUID.fromString(parsed.getBody().getSubject());
    } catch (ExpiredJwtException exception) {
      throw new BusinessException(ErrorCode.REFRESH_TOKEN_EXPIRED);
    } catch (BusinessException exception) {
      throw exception;
    } catch (JwtException | IllegalArgumentException exception) {
      throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
    }
  }

  public boolean isValidAccessToken(String accessToken) {
    try {
      Jws<Claims> parsed = parser().parseClaimsJws(accessToken);
      return ACCESS_TOKEN_TYPE.equals(parsed.getBody().get(TOKEN_TYPE_CLAIM, String.class));
    } catch (JwtException | IllegalArgumentException exception) {
      return false;
    }
  }

  public UUID getAccessTokenUserId(String accessToken) {
    try {
      return UUID.fromString(parser().parseClaimsJws(accessToken).getBody().getSubject());
    } catch (JwtException | IllegalArgumentException exception) {
      throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
    }
  }

  private String createToken(UUID userId, String tokenType, Instant now, Instant expiresAt) {
    return Jwts.builder()
        .setIssuer(ISSUER)
        .setSubject(userId.toString())
        .setId(UUID.randomUUID().toString())
        .claim(TOKEN_TYPE_CLAIM, tokenType)
        .setIssuedAt(Date.from(now))
        .setExpiration(Date.from(expiresAt))
        .signWith(signingKey)
        .compact();
  }

  private io.jsonwebtoken.JwtParser parser() {
    return Jwts.parserBuilder()
        .requireIssuer(ISSUER)
        .setSigningKey(signingKey)
        .setClock(() -> Date.from(clock.instant()))
        .build();
  }

  private Key createSigningKey(String encodedSecret) {
    if (encodedSecret == null || encodedSecret.isBlank()) {
      throw new BusinessException(ErrorCode.JWT_NOT_CONFIGURED);
    }
    try {
      byte[] decoded = Base64.getDecoder().decode(encodedSecret);
      if (decoded.length < HMAC_KEY_LENGTH_BYTES) {
        throw new IllegalStateException("JWT_SECRET must decode to at least 32 bytes");
      }
      return Keys.hmacShaKeyFor(decoded);
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException("JWT_SECRET must be Base64 encoded", exception);
    }
  }

  public static class IssuedTokenPair {

    private final String accessToken;
    private final String refreshToken;
    private final OffsetDateTime accessExpiresAt;
    private final OffsetDateTime refreshExpiresAt;

    public IssuedTokenPair(
        String accessToken,
        String refreshToken,
        OffsetDateTime accessExpiresAt,
        OffsetDateTime refreshExpiresAt) {
      this.accessToken = accessToken;
      this.refreshToken = refreshToken;
      this.accessExpiresAt = accessExpiresAt;
      this.refreshExpiresAt = refreshExpiresAt;
    }

    public String getAccessToken() {
      return accessToken;
    }

    public String getRefreshToken() {
      return refreshToken;
    }

    public OffsetDateTime getAccessExpiresAt() {
      return accessExpiresAt;
    }

    public OffsetDateTime getRefreshExpiresAt() {
      return refreshExpiresAt;
    }
  }
}
