package com.silvertown.domain.voice.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.silvertown.domain.voice.dto.VoiceSettingsRequest;
import com.silvertown.domain.voice.dto.VoiceSettingsResponse;
import com.silvertown.domain.voice.service.VoiceSettingsService;
import com.silvertown.global.common.exception.GlobalExceptionHandler;
import com.silvertown.global.common.filter.RequestIdFilter;
import com.silvertown.global.security.AuthenticatedUserId;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;

class VoiceSettingsControllerContractTest {
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private VoiceSettingsService voiceSettingsService;
    private VoiceSettingsResponse defaultResponse;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        voiceSettingsService = Mockito.mock(VoiceSettingsService.class);
        defaultResponse = new VoiceSettingsResponse(
                "ko-KR-JiMinNeural", new BigDecimal("1.05"), new BigDecimal("0.97"),
                new BigDecimal("1.00"), OffsetDateTime.parse("2026-09-02T10:15:00+09:00"));
        Mockito.when(voiceSettingsService.get(USER_ID.toString())).thenReturn(defaultResponse);
        Mockito.when(voiceSettingsService.update(Mockito.eq(USER_ID.toString()), Mockito.any())).thenReturn(defaultResponse);

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new VoiceSettingsController(voiceSettingsService, new AuthenticatedUserId()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void declaresJsonAsTheOnlyResponseMediaType() {
        RequestMapping mapping = VoiceSettingsController.class.getAnnotation(RequestMapping.class);

        assertEquals(1, mapping.produces().length);
        assertEquals(MediaType.APPLICATION_JSON_VALUE, mapping.produces()[0]);
    }

    @Test
    void getsVoiceSettingsAtDocumentedPath() throws Exception {
        JsonNode response = objectMapper.readTree(mockMvc.perform(get("/api/users/me/voice-settings")
                        .principal(authentication()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());

        assertEquals("ko-KR-JiMinNeural", response.get("ttsVoice").asText());
        assertEquals(1.05, response.get("speechRateMultiplier").asDouble());
        assertEquals(0.97, response.get("pitchMultiplier").asDouble());
        assertEquals(1.00, response.get("volumeMultiplier").asDouble());
    }

    @Test
    void updatesVoiceSettingsWithDocumentedRequest() throws Exception {
        JsonNode response = objectMapper.readTree(mockMvc.perform(put("/api/users/me/voice-settings")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ttsVoice\":\"ko-KR-GookMinNeural\",\"volumeMultiplier\":1.10}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());

        assertEquals("ko-KR-JiMinNeural", response.get("ttsVoice").asText());
        assertEquals(0.97, response.get("pitchMultiplier").asDouble());

        ArgumentCaptor<VoiceSettingsRequest> requestCaptor = ArgumentCaptor.forClass(VoiceSettingsRequest.class);
        Mockito.verify(voiceSettingsService).update(Mockito.eq(USER_ID.toString()), requestCaptor.capture());
        assertEquals("ko-KR-GookMinNeural", requestCaptor.getValue().getTtsVoice());
        assertEquals(new BigDecimal("1.10"), requestCaptor.getValue().getVolumeMultiplier());
    }

    @Test
    void rejectsOutOfRangeMultiplierAndEmptyRequest() throws Exception {
        mockMvc.perform(put("/api/users/me/voice-settings")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"speechRateMultiplier\":1.21}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/users/me/voice-settings")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnauthenticatedRequest() throws Exception {
        JsonNode response = objectMapper.readTree(mockMvc.perform(get("/api/users/me/voice-settings"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsByteArray());

        assertEquals("AUTHENTICATION_REQUIRED", response.get("code").asText());
    }

    @Test
    void usesOnlyTheAuthenticatedUsersIdWhenLoadingSettings() throws Exception {
        VoiceSettingsResponse otherUsersResponse = new VoiceSettingsResponse(
                "ko-KR-GookMinNeural", new BigDecimal("1.10"), new BigDecimal("0.97"),
                new BigDecimal("1.15"), OffsetDateTime.parse("2026-09-02T10:20:00+09:00"));
        Mockito.when(voiceSettingsService.get(OTHER_USER_ID.toString())).thenReturn(otherUsersResponse);

        JsonNode response = objectMapper.readTree(mockMvc.perform(get("/api/users/me/voice-settings")
                        .principal(authentication(OTHER_USER_ID)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());

        assertEquals("ko-KR-GookMinNeural", response.get("ttsVoice").asText());
        Mockito.verify(voiceSettingsService).get(OTHER_USER_ID.toString());
        Mockito.verify(voiceSettingsService, Mockito.never()).get(USER_ID.toString());
    }

    private UsernamePasswordAuthenticationToken authentication() {
        return authentication(USER_ID);
    }

    private UsernamePasswordAuthenticationToken authentication(UUID userId) {
        return UsernamePasswordAuthenticationToken.authenticated(userId.toString(), null, List.of());
    }
}
