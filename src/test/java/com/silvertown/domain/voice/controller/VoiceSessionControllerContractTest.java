package com.silvertown.domain.voice.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.silvertown.domain.voice.dto.VoiceSessionDetailResponse;
import com.silvertown.domain.voice.dto.VoiceSessionResponse;
import com.silvertown.domain.voice.enums.DialogueStep;
import com.silvertown.domain.voice.enums.SttMode;
import com.silvertown.domain.voice.enums.VoiceFlowType;
import com.silvertown.domain.voice.enums.VoiceSessionEntryPoint;
import com.silvertown.domain.voice.enums.VoiceSessionStatus;
import com.silvertown.domain.voice.service.VoiceSessionService;
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
import org.springframework.web.bind.annotation.RequestMapping;

class VoiceSessionControllerContractTest {
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SESSION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        VoiceSessionService service = Mockito.mock(VoiceSessionService.class);
        Mockito.when(service.create(Mockito.eq(USER_ID.toString()), Mockito.any()))
                .thenReturn(new VoiceSessionResponse(
                        SESSION_ID.toString(), VoiceSessionEntryPoint.GENERAL_FINANCE,
                        VoiceSessionStatus.LISTENING, DialogueStep.AWAITING_INPUT,
                        VoiceFlowType.GENERAL_FINANCE, SttMode.CLIENT,
                        OffsetDateTime.parse("2026-09-02T10:15:00+09:00"),
                        "안녕하세요. 잔액, 거래 내역, 금융 일정 중 필요한 내용을 말씀해 주세요.", null));
        Mockito.when(service.get(Mockito.eq(USER_ID.toString()), Mockito.eq(SESSION_ID.toString())))
                .thenReturn(new VoiceSessionDetailResponse(
                        SESSION_ID.toString(), VoiceSessionEntryPoint.GENERAL_FINANCE,
                        VoiceSessionStatus.LISTENING, DialogueStep.AWAITING_INPUT,
                        VoiceFlowType.GENERAL_FINANCE, SttMode.CLIENT,
                        OffsetDateTime.parse("2026-09-02T10:15:00+09:00"), null, null, null, null));

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new VoiceSessionController(service, new AuthenticatedUserId()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void createsSessionWithDocumentedPathAndResponseFields() throws Exception {
        JsonNode response = objectMapper.readTree(mockMvc.perform(post("/api/voice/sessions")
                        .principal(authentication())
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryPoint\":\"GENERAL_FINANCE\"}"))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsByteArray());

        assertEquals(SESSION_ID.toString(), response.get("sessionId").asText());
        assertEquals("GENERAL_FINANCE", response.get("entryPoint").asText());
        assertEquals("LISTENING", response.get("status").asText());
        assertEquals("AWAITING_INPUT", response.get("currentStep").asText());
        assertEquals("GENERAL_FINANCE", response.get("flowType").asText());
        assertEquals("CLIENT", response.get("sttMode").asText());
        assertEquals("안녕하세요. 잔액, 거래 내역, 금융 일정 중 필요한 내용을 말씀해 주세요.",
                response.get("firstPrompt").asText());
    }

    @Test
    void getsOwnedSessionAtDocumentedPath() throws Exception {
        JsonNode response = objectMapper.readTree(mockMvc.perform(get("/api/voice/sessions/{sessionId}", SESSION_ID)
                        .principal(authentication())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsByteArray());

        assertEquals(SESSION_ID.toString(), response.get("sessionId").asText());
        assertEquals("LISTENING", response.get("status").asText());
    }

    @Test
    void rejectsSessionRequestsWithoutAuthentication() throws Exception {
        JsonNode response = objectMapper.readTree(mockMvc.perform(get("/api/voice/sessions/{sessionId}", SESSION_ID))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsByteArray());

        assertEquals("AUTHENTICATION_REQUIRED", response.get("code").asText());
    }

    @Test
    void rejectsNonCanonicalSessionIdWithStandardBadRequest() throws Exception {
        JsonNode response = objectMapper.readTree(mockMvc.perform(get("/api/voice/sessions/{sessionId}",
                        "10000000-0000-0000-0000-00000000000A")
                        .principal(authentication()))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsByteArray());

        assertEquals("INVALID_REQUEST", response.get("code").asText());
    }

    @Test
    void declaresJsonAsTheOnlyResponseMediaType() {
        RequestMapping mapping = VoiceSessionController.class.getAnnotation(RequestMapping.class);

        assertEquals(1, mapping.produces().length);
        assertEquals(MediaType.APPLICATION_JSON_VALUE, mapping.produces()[0]);
    }

    private UsernamePasswordAuthenticationToken authentication() {
        return UsernamePasswordAuthenticationToken.authenticated(USER_ID.toString(), null, List.of());
    }
}
