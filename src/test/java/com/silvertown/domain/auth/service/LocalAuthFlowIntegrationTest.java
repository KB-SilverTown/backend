package com.silvertown.domain.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvertown.domain.auth.dto.AuthResponse;
import com.silvertown.domain.auth.dto.LoginRequest;
import com.silvertown.domain.auth.dto.SignUpRequest;
import com.silvertown.global.config.RootConfig;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;
import javax.annotation.PostConstruct;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@EnabledIfSystemProperty(named = "runLocalAuthSmoke", matches = "true")
class LocalAuthFlowIntegrationTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static AnnotationConfigApplicationContext context;
  private static UUID createdUserId;

  @BeforeAll
  static void setUpContext() throws IOException {
    loadDotenvIntoSystemProperties();
    context = new AnnotationConfigApplicationContext();
    context.register(RootConfig.class, LocalAuthTestConfiguration.class);
    context.refresh();
  }

  @AfterAll
  static void cleanUp() {
    if (context == null) {
      return;
    }
    if (createdUserId != null) {
      JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
      String userId = createdUserId.toString();
      jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", userId);
      jdbcTemplate.update("DELETE FROM user_consents WHERE user_id = ?", userId);
      jdbcTemplate.update("DELETE FROM bank_accounts WHERE user_id = ?", userId);
      jdbcTemplate.update("DELETE FROM user_profiles WHERE user_id = ?", userId);
      jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", userId);
    }
    context.close();
  }

  @Test
  void signUpLoginRefreshAndLogoutUseTheLocalMySqlSchema() throws Exception {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    AuthService authService = context.getBean(AuthService.class);

    AuthResponse signedUp = authService.signUp(OBJECT_MAPPER.readValue(signUpJson(suffix), SignUpRequest.class));
    createdUserId = signedUp.getUserId();
    assertNotNull(signedUp.getAccessToken());
    assertNotNull(signedUp.getRefreshToken());
    assertEquals("로컬보호자", context.getBean(JdbcTemplate.class).queryForObject(
        "SELECT emergency_contact_name FROM user_profiles WHERE user_id = ?",
        String.class,
        createdUserId.toString()));

    AuthResponse loggedIn = authService.login(OBJECT_MAPPER.readValue(
        "{\"loginId\":\"local-" + suffix + "\",\"password\":\"local-password-1\"}",
        LoginRequest.class));
    assertEquals(createdUserId, loggedIn.getUserId());

    AuthResponse refreshed = authService.refresh(loggedIn.getRefreshToken());
    assertEquals(createdUserId, refreshed.getUserId());
    assertNotEquals(loggedIn.getRefreshToken(), refreshed.getRefreshToken());

    authService.logout(refreshed.getRefreshToken());
    Integer revokedCount = context.getBean(JdbcTemplate.class).queryForObject(
        "SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NOT NULL",
        Integer.class,
        createdUserId.toString());
    assertNotNull(revokedCount);
    assertEquals(2, revokedCount.intValue());
  }

  private String signUpJson(String suffix) {
    String numericSuffix = suffix.replaceAll("[a-f]", "1");
    return "{"
        + "\"loginId\":\"local-" + suffix + "\",\"password\":\"local-password-1\","
        + "\"name\":\"로컬테스트\",\"residentRegistrationNumber\":\"900101-1"
        + numericSuffix.substring(0, 6) + "\","
        + "\"gender\":\"MALE\",\"postalCode\":\"06234\",\"address\":\"서울특별시 강남구 테헤란로 1\","
        + "\"detailAddress\":\"101호\",\"bankCode\":\"004\",\"accountNumber\":\"1234" + suffix + "\","
        + "\"phone\":\"010" + suffix.substring(0, 8).replaceAll("[a-f]", "1") + "\","
        + "\"emergencyContact\":{\"name\":\"로컬보호자\",\"phone\":\"011" + suffix.substring(2, 10).replaceAll("[a-f]", "2")
        + "\",\"relationship\":\"자녀\"},\"consents\":["
        + "{\"type\":\"TERMS_OF_SERVICE\",\"agreed\":true,\"documentVersion\":\"v1\"},"
        + "{\"type\":\"PRIVACY_COLLECTION\",\"agreed\":true,\"documentVersion\":\"v1\"},"
        + "{\"type\":\"MYDATA_FINANCIAL\",\"agreed\":true,\"documentVersion\":\"v1\"},"
        + "{\"type\":\"AI_VOICE_DATA\",\"agreed\":true,\"documentVersion\":\"v1\"}]}";
  }

  private static void loadDotenvIntoSystemProperties() throws IOException {
    Properties dotenv = new Properties();
    try (InputStream stream = Files.newInputStream(Path.of(".env"))) {
      dotenv.load(stream);
    }
    dotenv.forEach((key, value) -> System.setProperty(key.toString(), value.toString()));
  }

  @Configuration
  static class LocalAuthTestConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
      return new BCryptPasswordEncoder();
    }
  }
}
