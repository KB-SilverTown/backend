package com.silvertown.domain.voice.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvertown.domain.voice.dto.VoiceReplayPayloadResponse;
import com.silvertown.domain.voice.dto.VoiceSessionEventResponse;
import com.silvertown.domain.voice.enums.DialogueStep;
import com.silvertown.domain.voice.service.VoiceSessionEventService;
import com.silvertown.global.common.exception.GlobalExceptionHandler;
import com.silvertown.global.common.filter.RequestIdFilter;
import com.silvertown.global.security.AuthenticatedUserId;
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

class VoiceSessionEventControllerContractTest {
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SESSION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID TURN_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        VoiceSessionEventService service = Mockito.mock(VoiceSessionEventService.class);
        Mockito.when(service.handle(Mockito.eq(USER_ID.toString()), Mockito.eq(SESSION_ID.toString()), Mockito.any()))
                .thenReturn(new VoiceSessionEventResponse(
                        DialogueStep.AWAITING_AMOUNT,
                        null,
                        new VoiceReplayPayloadResponse(
                                "김철수님께 돈 보내기를 진행하고 있어요. 보낼 금액을 다시 말씀해 주세요.",
                                "<speak>김철수님께 돈 보내기를 진행하고 있어요.</speak>",
                                objectMapper.valueToTree(Map.of("recipient", "김철수")))));

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new VoiceSessionEventController(service, new AuthenticatedUserId()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void replaysAtTheDocumentedEventPath() throws Exception {
        JsonNode response = objectMapper.readTree(mockMvc.perform(post(
                        "/api/voice/sessions/{sessionId}/events", SESSION_ID)
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"REPLAY\",\"turnId\":\"" + TURN_ID + "\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsByteArray());

        assertEquals("AWAITING_AMOUNT", response.get("state").asText());
        assertEquals("김철수님께 돈 보내기를 진행하고 있어요. 보낼 금액을 다시 말씀해 주세요.",
                response.get("replayPayload").get("ttsText").asText());
    }

    @Test
    void rejectsTransferWebSocketEventsAtTheHttpEndpoint() throws Exception {
        mockMvc.perform(post("/api/voice/sessions/{sessionId}/events", SESSION_ID)
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"BARGE_IN\",\"turnId\":\"" + TURN_ID + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsNonCanonicalSessionIdAndTurnId() throws Exception {
        mockMvc.perform(post("/api/voice/sessions/{sessionId}/events", "not-a-uuid")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"REPLAY\",\"turnId\":\"" + TURN_ID + "\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/voice/sessions/{sessionId}/events", SESSION_ID)
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"REPLAY\",\"turnId\":\"not-a-uuid\"}"))
                .andExpect(status().isBadRequest());
    }

    private UsernamePasswordAuthenticationToken authentication() {
        return UsernamePasswordAuthenticationToken.authenticated(USER_ID.toString(), null, List.of());
    }
}
