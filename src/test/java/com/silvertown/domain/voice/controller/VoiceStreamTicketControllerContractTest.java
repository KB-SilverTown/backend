package com.silvertown.domain.voice.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.silvertown.domain.voice.dto.VoiceStreamTicketResponse;
import com.silvertown.domain.voice.service.VoiceStreamTicketService;
import com.silvertown.global.common.exception.GlobalExceptionHandler;
import com.silvertown.global.common.filter.RequestIdFilter;
import com.silvertown.global.security.AuthenticatedUserId;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class VoiceStreamTicketControllerContractTest {
    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String SESSION_ID = "10000000-0000-0000-0000-000000000001";

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        VoiceStreamTicketService service = Mockito.mock(VoiceStreamTicketService.class);
        Mockito.when(service.issue(USER_ID, SESSION_ID)).thenReturn(new VoiceStreamTicketResponse(
                "vst_opaque-ticket", OffsetDateTime.parse("2026-09-09T18:01:00+09:00")));
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new VoiceStreamTicketController(service, new AuthenticatedUserId()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void issuesTicketAtDocumentedPathWithCreatedResponse() throws Exception {
        JsonNode response = objectMapper.readTree(mockMvc.perform(post(
                        "/api/voice/sessions/{sessionId}/stream-ticket", SESSION_ID)
                        .principal(authentication())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsByteArray());

        assertEquals("vst_opaque-ticket", response.get("ticket").asText());
        assertEquals("2026-09-09T18:01:00+09:00", response.get("expiresAt").asText());
    }

    @Test
    void rejectsUnauthenticatedTicketRequest() throws Exception {
        JsonNode response = objectMapper.readTree(mockMvc.perform(post(
                        "/api/voice/sessions/{sessionId}/stream-ticket", SESSION_ID))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsByteArray());

        assertEquals("AUTHENTICATION_REQUIRED", response.get("code").asText());
    }

    @Test
    void rejectsNonCanonicalSessionId() throws Exception {
        JsonNode response = objectMapper.readTree(mockMvc.perform(post(
                        "/api/voice/sessions/{sessionId}/stream-ticket", "not-a-uuid")
                        .principal(authentication()))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsByteArray());

        assertEquals("INVALID_REQUEST", response.get("code").asText());
    }

    private UsernamePasswordAuthenticationToken authentication() {
        return UsernamePasswordAuthenticationToken.authenticated(USER_ID, null, List.of());
    }
}
