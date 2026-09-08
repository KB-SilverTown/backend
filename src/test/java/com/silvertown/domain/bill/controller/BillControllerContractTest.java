package com.silvertown.domain.bill.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.silvertown.domain.bill.dto.BillPaymentResultResponse;
import com.silvertown.domain.bill.dto.BillMonthlySummaryResponse;
import com.silvertown.domain.bill.dto.BillSummary;
import com.silvertown.domain.bill.enums.BillStatus;
import com.silvertown.domain.bill.service.BillService;
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

class BillControllerContractTest {
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BILL_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID PAYMENT_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private MockMvc mockMvc;
    private BillService billService;

    @BeforeEach
    void setUp() {
        billService = Mockito.mock(BillService.class);
        Mockito.when(billService.execute(
                        Mockito.eq(USER_ID),
                        Mockito.eq(BILL_ID),
                        Mockito.eq("bill-payment-key-1"),
                        Mockito.any()))
                .thenReturn(new BillPaymentResultResponse(
                        PAYMENT_ID,
                        BILL_ID,
                        "SUCCESS",
                        48200L,
                        OffsetDateTime.parse("2026-09-03T10:00:00+09:00")));
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new BillController(billService, new AuthenticatedUserId()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void executesMockPaymentWithDocumentedHeaderAndJsonResponse() throws Exception {
        JsonNode response = objectMapper.readTree(mockMvc.perform(post("/api/bills/{billId}/execute", BILL_ID)
                        .principal(authentication())
                        .header("Idempotency-Key", "bill-payment-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmationToken\":\"confirmation-token\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsByteArray());

        assertEquals(PAYMENT_ID.toString(), response.get("paymentId").asText());
        assertEquals(BILL_ID.toString(), response.get("billId").asText());
        assertEquals("SUCCESS", response.get("status").asText());
    }

    @Test
    void returnsMonthlyBillSummaryAsJson() throws Exception {
        Mockito.when(billService.summarizeMonth(USER_ID, 2026, 9)).thenReturn(new BillMonthlySummaryResponse(
                "2026-09",
                48_200L,
                0L,
                48_200L,
                1L,
                0L,
                1L,
                false,
                List.of(new BillSummary(BILL_ID, BillStatus.DRAFT, "한국전력", 48_200L,
                        java.time.LocalDate.of(2026, 9, 25), false))));

        JsonNode response = objectMapper.readTree(mockMvc.perform(get("/api/bills/monthly-summary")
                        .principal(authentication())
                        .param("year", "2026")
                        .param("month", "9"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsByteArray());

        assertEquals("2026-09", response.get("yearMonth").asText());
        assertEquals(48_200L, response.get("unpaidAmount").asLong());
        assertEquals("한국전력", response.get("items").get(0).get("payee").asText());
    }

    private UsernamePasswordAuthenticationToken authentication() {
        return UsernamePasswordAuthenticationToken.authenticated(USER_ID.toString(), null, List.of());
    }
}
