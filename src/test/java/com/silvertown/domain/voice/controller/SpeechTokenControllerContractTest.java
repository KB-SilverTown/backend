package com.silvertown.domain.voice.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.silvertown.domain.voice.dto.SpeechTokenResponse;
import com.silvertown.domain.voice.service.SpeechTokenService;
import com.silvertown.global.common.exception.GlobalExceptionHandler;
import com.silvertown.global.common.filter.RequestIdFilter;
import com.silvertown.global.security.AuthenticatedUserId;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SpeechTokenControllerContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SpeechTokenService service = () -> new SpeechTokenResponse(
                "short-lived-token",
                "koreacentral",
                OffsetDateTime.parse("2026-09-02T00:09:00Z")
        );
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new SpeechTokenController(service, new AuthenticatedUserId()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void issuesSpeechTokenForAuthenticatedUserWithoutRequestBody() throws Exception {
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        UUID.fromString("00000000-0000-0000-0000-000000000001").toString(),
                        null,
                        java.util.List.of());

        JsonNode response = objectMapper.readTree(mockMvc.perform(post("/api/voice/speech-token")
                        .accept(MediaType.APPLICATION_JSON)
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsByteArray());

        assertEquals("short-lived-token", response.get("token").asText());
        assertEquals("koreacentral", response.get("region").asText());
        assertEquals("2026-09-02T00:09:00Z", response.get("expiresAt").asText());
    }

    @Test
    void rejectsRequestWithoutAuthentication() throws Exception {
        JsonNode response = objectMapper.readTree(mockMvc.perform(post("/api/voice/speech-token"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsByteArray());

        assertEquals("AUTHENTICATION_REQUIRED", response.get("code").asText());
    }
}
