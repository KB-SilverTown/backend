package com.silvertown.domain.auth.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.silvertown.domain.auth.dto.AuthResponse;
import com.silvertown.domain.auth.dto.LoginRequest;
import com.silvertown.domain.auth.dto.SignUpRequest;
import com.silvertown.domain.auth.service.AuthService;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class AuthControllerContractTest {

  private final ObjectMapper objectMapper = new ObjectMapper()
      .registerModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  private final UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    AuthService authService = new AuthService() {
      @Override
      public AuthResponse signUp(SignUpRequest request) {
        return response();
      }

      @Override
      public AuthResponse login(LoginRequest request) {
        return response();
      }

      @Override
      public AuthResponse refresh(String refreshToken) {
        return response();
      }

      @Override
      public void logout(String refreshToken) {
      }
    };
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
        .setValidator(validator)
        .setMessageConverters(
            new MappingJackson2HttpMessageConverter(objectMapper),
            new MappingJackson2XmlHttpMessageConverter())
        .build();
  }

  @Test
  void signUpReturnsCreatedAuthResponse() throws Exception {
    JsonNode response = objectMapper.readTree(mockMvc.perform(post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validSignUpRequest()))
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getContentAsByteArray());

    assertEquals(userId.toString(), response.get("userId").asText());
    assertEquals("access-token", response.get("accessToken").asText());
    assertEquals("refresh-token", response.get("refreshToken").asText());
    assertEquals("2026-09-01T12:00:00+09:00", response.get("expiresAt").asText());
  }

  @Test
  void signUpRejectsMissingEmergencyContactName() throws Exception {
    JsonNode response = objectMapper.readTree(mockMvc.perform(post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validSignUpRequest().replace("\"name\":\"김철수\",", "")))
        .andExpect(status().isBadRequest())
        .andReturn()
        .getResponse()
        .getContentAsByteArray());

    assertEquals("SIGNUP_REQUEST_INVALID", response.get("code").asText());
  }

  @Test
  void loginValidationUsesAuthRequestInvalidErrorCode() throws Exception {
    JsonNode response = objectMapper.readTree(mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest())
        .andReturn()
        .getResponse()
        .getContentAsByteArray());

    assertEquals("AUTH_REQUEST_INVALID", response.get("code").asText());
  }

  @Test
  void logoutReturnsNoContent() throws Exception {
    mockMvc.perform(post("/api/auth/logout")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"refreshToken\":\"token\"}"))
        .andExpect(status().isNoContent());
  }

  @Test
  void loginReturnsJsonWhenXmlConverterIsAvailable() throws Exception {
    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"loginId\":\"senior01\",\"password\":\"password1\"}"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
  }

  @Test
  void loginRejectsXmlOnlyResponseNegotiation() throws Exception {
    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_XML)
            .content("{\"loginId\":\"senior01\",\"password\":\"password1\"}"))
        .andExpect(status().isNotAcceptable());
  }

  private AuthResponse response() {
    return new AuthResponse(
        "access-token",
        "refresh-token",
        OffsetDateTime.parse("2026-09-01T12:00:00+09:00"),
        userId);
  }

  private String validSignUpRequest() {
    return "{"
        + "\"loginId\":\"senior01\",\"password\":\"password1\",\"name\":\"홍길동\","
        + "\"residentRegistrationNumber\":\"9001011234567\",\"gender\":\"MALE\","
        + "\"postalCode\":\"06234\",\"address\":\"서울특별시 강남구 테헤란로 1\","
        + "\"detailAddress\":\"101호\",\"bankCode\":\"004\",\"accountNumber\":\"1234567890\","
        + "\"phone\":\"01012345678\",\"emergencyContact\":{\"name\":\"김철수\",\"phone\":\"01098765432\",\"relationship\":\"자녀\"},"
        + "\"consents\":["
        + "{\"type\":\"TERMS_OF_SERVICE\",\"agreed\":true,\"documentVersion\":\"v1\"},"
        + "{\"type\":\"PRIVACY_COLLECTION\",\"agreed\":true,\"documentVersion\":\"v1\"},"
        + "{\"type\":\"MYDATA_FINANCIAL\",\"agreed\":true,\"documentVersion\":\"v1\"},"
        + "{\"type\":\"AI_VOICE_DATA\",\"agreed\":true,\"documentVersion\":\"v1\"}]}";
  }
}
