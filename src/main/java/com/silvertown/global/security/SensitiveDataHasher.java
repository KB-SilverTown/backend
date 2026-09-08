package com.silvertown.global.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SensitiveDataHasher {
  private final byte[] guardianVerificationHmacKey;

  @Autowired
  public SensitiveDataHasher(
      @Value("${guardian.verification.hmac-secret:}") String guardianVerificationHmacSecret) {
    if (guardianVerificationHmacSecret == null || guardianVerificationHmacSecret.isBlank()) {
      throw new IllegalStateException("guardian.verification.hmac-secret must be configured");
    }
    guardianVerificationHmacKey = guardianVerificationHmacSecret.getBytes(StandardCharsets.UTF_8);
  }

  public String hash(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  public String hashNumericValue(String value) {
    return hash(value.replaceAll("[^0-9]", ""));
  }

  /** Stores short-lived verification codes with a server-held secret, not a reversible fast hash. */
  public String hmacGuardianVerificationCode(String code) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(guardianVerificationHmacKey, "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(code.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.GeneralSecurityException exception) {
      throw new IllegalStateException("HMAC-SHA-256 is unavailable", exception);
    }
  }
}
