package com.silvertown.domain.auth.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvertown.domain.auth.dto.UserProfileResponse;
import com.silvertown.domain.auth.service.AuthService;
import com.silvertown.global.common.exception.GlobalExceptionHandler;
import com.silvertown.global.security.AuthenticatedUserId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class UserProfileControllerContractTest {

  private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private final ObjectMapper objectMapper = new ObjectMapper();
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    AuthService authService = mock(AuthService.class);
    when(authService.getCurrentUserProfile(USER_ID)).thenReturn(new UserProfileResponse(
        USER_ID,
        "senior01",
        "홍길동",
        "010-1234-5678",
        "06234",
        "서울특별시 강남구 테헤란로 1",
        "101호"));

    mockMvc = MockMvcBuilders.standaloneSetup(
            new UserProfileController(authService, new AuthenticatedUserId()))
        .setControllerAdvice(new GlobalExceptionHandler())
        .setMessageConverters(
            new MappingJackson2XmlHttpMessageConverter(),
            new MappingJackson2HttpMessageConverter(objectMapper))
        .build();
  }

  @Test
  void currentUserProfileMatchesTheFrontendContract() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/users/me")
            .principal(authentication())
            .accept(MediaType.ALL))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andReturn();

    JsonNode response = objectMapper.readTree(result.getResponse().getContentAsByteArray());
    assertEquals(USER_ID.toString(), response.get("userId").asText());
    assertEquals("홍길동", response.get("name").asText());
    assertEquals("010-1234-5678", response.get("phone").asText());
    assertEquals("서울특별시 강남구 테헤란로 1", response.get("address").asText());
    assertFalse(response.has("residentRegistrationNumber"));
    assertFalse(response.has("accountNumber"));
    assertFalse(response.has("emergencyContact"));
  }

  @Test
  void currentUserProfileRequiresAuthentication() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/users/me"))
        .andExpect(status().isUnauthorized())
        .andReturn();

    JsonNode response = objectMapper.readTree(result.getResponse().getContentAsByteArray());
    assertEquals("AUTHENTICATION_REQUIRED", response.get("code").asText());
  }

  private UsernamePasswordAuthenticationToken authentication() {
    return new UsernamePasswordAuthenticationToken(USER_ID.toString(), "", List.of());
  }
}
