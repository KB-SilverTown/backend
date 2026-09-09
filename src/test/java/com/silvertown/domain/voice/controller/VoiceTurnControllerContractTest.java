package com.silvertown.domain.voice.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvertown.domain.voice.dto.VoiceTurnResponse;
import com.silvertown.domain.voice.enums.DialogueStep;
import com.silvertown.domain.voice.service.VoiceTurnService;
import com.silvertown.global.common.exception.GlobalExceptionHandler;
import com.silvertown.global.common.filter.RequestIdFilter;
import com.silvertown.global.security.AuthenticatedUserId;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class VoiceTurnControllerContractTest {
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SESSION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID TURN_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        VoiceTurnService service = Mockito.mock(VoiceTurnService.class);
        Mockito.when(service.process(Mockito.eq(USER_ID.toString()), Mockito.eq(SESSION_ID.toString()), Mockito.any()))
                .thenReturn(new VoiceTurnResponse(
                        SESSION_ID.toString(),
                        TURN_ID.toString(),
                        DialogueStep.AWAITING_AMOUNT,
                        "TRANSFER",
                        "TRANSFER_RECIPIENT_CANDIDATES",
                        Map.of("recipient", "김철수"),
                        new BigDecimal("0.95"),
                        "김철수 님에게 보낼 금액을 말씀해 주세요.",
                        "<speak>김철수 님에게 보낼 금액을 말씀해 주세요.</speak>",
                        objectMapper.valueToTree(Map.of("recipient", "김철수")),
                        objectMapper.valueToTree(Map.of("name", "amount")),
                        objectMapper.valueToTree(Map.of("recipient", "김철수")),
                        "ASK_AMOUNT"));

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new VoiceTurnController(service, new AuthenticatedUserId()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void processesTurnAtDocumentedPathWithoutPromptType() throws Exception {
        JsonNode response = objectMapper.readTree(mockMvc.perform(post(
                        "/api/voice/sessions/{sessionId}/turns", SESSION_ID)
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"turnId\":\"" + TURN_ID + "\","
                                + "\"transcript\":\"김철수에게 오만원 보내줘\","
                                + "\"sttConfidence\":0.95,\"inputType\":\"VOICE\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsByteArray());

        assertEquals("AWAITING_AMOUNT", response.get("state").asText());
        assertEquals(SESSION_ID.toString(), response.get("sessionId").asText());
        assertEquals(TURN_ID.toString(), response.get("turnId").asText());
        assertEquals("TRANSFER_RECIPIENT_CANDIDATES", response.get("requestedFunction").asText());
        assertEquals("ASK_AMOUNT", response.get("nextAction").asText());
        assertFalse(response.has("promptType"));
    }

    @Test
    void rejectsNonCanonicalSessionIdWithStandardBadRequest() throws Exception {
        JsonNode response = objectMapper.readTree(mockMvc.perform(post(
                        "/api/voice/sessions/{sessionId}/turns", "not-a-uuid")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsByteArray());

        assertEquals("INVALID_REQUEST", response.get("code").asText());
    }

    private UsernamePasswordAuthenticationToken authentication() {
        return UsernamePasswordAuthenticationToken.authenticated(USER_ID.toString(), null, List.of());
    }
}
