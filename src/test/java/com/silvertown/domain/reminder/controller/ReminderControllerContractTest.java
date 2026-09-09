package com.silvertown.domain.reminder.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.silvertown.domain.reminder.dto.ReminderListResponse;
import com.silvertown.domain.reminder.dto.ReminderResponse;
import com.silvertown.domain.reminder.enums.ReminderStatus;
import com.silvertown.domain.reminder.service.ReminderService;
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

class ReminderControllerContractTest {
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID REMINDER_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ReminderService reminderService = Mockito.mock(ReminderService.class);
        ReminderResponse response = new ReminderResponse(
                REMINDER_ID,
                "전기요금 납부",
                null,
                OffsetDateTime.parse("2026-09-09T09:00:00+09:00"),
                ReminderStatus.SCHEDULED);
        Mockito.when(reminderService.find(Mockito.eq(USER_ID), Mockito.eq("SCHEDULED"),
                        Mockito.any(), Mockito.any()))
                .thenReturn(new ReminderListResponse(List.of(response)));
        Mockito.when(reminderService.create(Mockito.eq(USER_ID), Mockito.any())).thenReturn(response);

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ReminderController(reminderService, new AuthenticatedUserId()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void returnsOwnedReminderListUsingDocumentedQueryAndResponseShape() throws Exception {
        JsonNode response = objectMapper.readTree(mockMvc.perform(get("/api/reminders")
                        .principal(authentication())
                        .param("status", "SCHEDULED")
                        .param("from", "2026-09-01T00:00:00+09:00")
                        .param("to", "2026-09-30T23:59:00+09:00"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());

        assertEquals(REMINDER_ID.toString(), response.get("items").get(0).get("id").asText());
        assertEquals("SCHEDULED", response.get("items").get(0).get("status").asText());
    }

    @Test
    void createsReminderWithDocumentedRequestAndResponseShape() throws Exception {
        JsonNode response = objectMapper.readTree(mockMvc.perform(post("/api/reminders")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"전기요금 납부\",\"scheduledAt\":\"2026-09-09T09:00:00+09:00\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsByteArray());

        assertEquals(REMINDER_ID.toString(), response.get("id").asText());
        assertEquals("전기요금 납부", response.get("title").asText());
        assertEquals("SCHEDULED", response.get("status").asText());
    }

    @Test
    void rejectsReminderRequestWithoutAuthentication() throws Exception {
        JsonNode response = objectMapper.readTree(mockMvc.perform(get("/api/reminders"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsByteArray());

        assertEquals("AUTHENTICATION_REQUIRED", response.get("code").asText());
    }

    private UsernamePasswordAuthenticationToken authentication() {
        return UsernamePasswordAuthenticationToken.authenticated(USER_ID.toString(), null, List.of());
    }
}
