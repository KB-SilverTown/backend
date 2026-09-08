package com.silvertown.domain.voice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvertown.domain.bill.dto.BillMonthlySummaryResponse;
import com.silvertown.domain.bill.dto.BillSummary;
import com.silvertown.domain.bill.enums.BillStatus;
import com.silvertown.domain.bill.service.BillService;
import com.silvertown.domain.voice.enums.DialogueStep;
import com.silvertown.domain.voice.enums.VoiceIntent;
import com.silvertown.domain.voice.enums.VoiceNextAction;
import com.silvertown.domain.voice.enums.VoiceRequestedFunction;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class BillVoiceResponseResolverTest {
    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String SESSION_ID = "10000000-0000-0000-0000-000000000001";
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-03T01:00:00Z"), ZoneOffset.UTC);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private BillService billService;
    private BillVoiceResponseResolver resolver;

    @BeforeEach
    void setUp() {
        billService = org.mockito.Mockito.mock(BillService.class);
        resolver = new BillVoiceResponseResolver(billService, objectMapper, CLOCK);
    }

    @Test
    void resolvesBillInquiryWithTrustedMonthlySummaryCard() {
        UUID billId = UUID.fromString("30000000-0000-0000-0000-000000000001");
        when(billService.summarizeMonth(UUID.fromString(USER_ID), 2026, 9)).thenReturn(
                new BillMonthlySummaryResponse(
                        "2026-09", 58200L, 10000L, 48200L, 2L, 1L, 1L, false,
                        List.of(new BillSummary(
                                billId, BillStatus.DRAFT, "한국전력", 48200L,
                                LocalDate.of(2026, 9, 25), false))));

        VoiceTurnAnalysisResult result = resolver.resolve(
                USER_ID,
                SESSION_ID,
                "2026년 9월 공과금 알려줘",
                analysis(VoiceRequestedFunction.BILL_INQUIRY, Map.of("year", 2026, "month", 9)));

        assertEquals("2026-09 미납 고지서는 1건, 총 48,200원입니다.", result.getTtsText());
        assertEquals("<speak>2026-09 미납 고지서는 1건, 총 48,200원입니다.</speak>", result.getTtsSsml());
        assertEquals("BILL_MONTHLY_SUMMARY", result.getDisplayCard().path("type").asText());
        assertEquals("BILL_LIST", result.getDisplayCard().path("screenCode").asText());
        assertEquals(2026, result.getDisplayCard().path("params").path("year").asInt());
        assertEquals(9, result.getDisplayCard().path("params").path("month").asInt());
        assertEquals(billId.toString(), result.getDisplayCard().path("items").get(0).path("billId").asText());
        verify(billService).summarizeMonth(UUID.fromString(USER_ID), 2026, 9);
    }

    @Test
    void requestsTheMonthAgainWhenOnlyOneDateSlotIsAvailable() {
        VoiceTurnAnalysisResult result = resolver.resolve(
                USER_ID,
                SESSION_ID,
                "2025년 공과금 알려줘",
                analysis(VoiceRequestedFunction.BILL_INQUIRY, Map.of("year", 2025)));

        assertEquals("조회할 연도와 월을 다시 말씀해 주세요.", result.getTtsText());
        assertEquals("BILL_PERIOD_REASK", result.getDisplayCard().path("type").asText());
        verify(billService, never()).summarizeMonth(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void completesAPartialMonthSlotFromTheLastMonthExpression() {
        when(billService.summarizeMonth(UUID.fromString(USER_ID), 2026, 8)).thenReturn(
                new BillMonthlySummaryResponse("2026-08", 1000L, 0L, 1000L, 1L, 0L, 1L, false, List.of()));

        VoiceTurnAnalysisResult result = resolver.resolve(
                USER_ID,
                SESSION_ID,
                "지난달 공과금 알려줘",
                analysis(VoiceRequestedFunction.BILL_INQUIRY, Map.of("month", "8")));

        assertEquals("2026-08 미납 고지서는 1건, 총 1,000원입니다.", result.getTtsText());
        verify(billService).summarizeMonth(UUID.fromString(USER_ID), 2026, 8);
    }

    @Test
    void resolvesTheLastMonthExpressionBeforeUsingTheCurrentMonthByDefault() {
        when(billService.summarizeMonth(UUID.fromString(USER_ID), 2026, 8)).thenReturn(
                new BillMonthlySummaryResponse("2026-08", 1000L, 0L, 1000L, 1L, 0L, 1L, false, List.of()));

        VoiceTurnAnalysisResult result = resolver.resolve(
                USER_ID,
                SESSION_ID,
                "지난달 고지서 알려줘",
                analysis(VoiceRequestedFunction.BILL_INQUIRY, Map.of()));

        assertEquals("2026-08 미납 고지서는 1건, 총 1,000원입니다.", result.getTtsText());
        verify(billService).summarizeMonth(UUID.fromString(USER_ID), 2026, 8);
    }

    @Test
    void doesNotOverwriteAnExplicitYearWithARelativeDateExpression() {
        VoiceTurnAnalysisResult result = resolver.resolve(
                USER_ID,
                SESSION_ID,
                "지난달 2025년 공과금 알려줘",
                analysis(VoiceRequestedFunction.BILL_INQUIRY, Map.of("year", 2025)));

        assertEquals("조회할 연도와 월을 다시 말씀해 주세요.", result.getTtsText());
        verify(billService, never()).summarizeMonth(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void requestsTheMonthAgainWhenTheRelativeDateAndExtractedMonthConflict() {
        VoiceTurnAnalysisResult result = resolver.resolve(
                USER_ID,
                SESSION_ID,
                "지난달 공과금 알려줘",
                analysis(VoiceRequestedFunction.BILL_INQUIRY, Map.of("month", 9)));

        assertEquals("조회할 연도와 월을 다시 말씀해 주세요.", result.getTtsText());
        verify(billService, never()).summarizeMonth(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void requestsTheMonthAgainWhenTheExtractedYearIsOutsideTheDatabaseRange() {
        VoiceTurnAnalysisResult result = resolver.resolve(
                USER_ID,
                SESSION_ID,
                "10000년 9월 공과금 알려줘",
                analysis(VoiceRequestedFunction.BILL_INQUIRY, Map.of("year", 10000, "month", 9)));

        assertEquals("조회할 연도와 월을 다시 말씀해 주세요.", result.getTtsText());
        verify(billService, never()).summarizeMonth(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void parsesStringAndDecimalDateSlotsWithoutDefaultingToTheCurrentMonth() {
        when(billService.summarizeMonth(UUID.fromString(USER_ID), 2026, 9)).thenReturn(emptySummary());

        resolver.resolve(
                USER_ID,
                SESSION_ID,
                "2026년 9월 공과금 알려줘",
                analysis(VoiceRequestedFunction.BILL_INQUIRY,
                        Map.of("year", "2026", "month", new BigDecimal("9.0"))));

        verify(billService).summarizeMonth(UUID.fromString(USER_ID), 2026, 9);
    }

    @Test
    void returnsAnEmptyBillMessageAndCardWhenThereAreNoUnpaidBills() {
        when(billService.summarizeMonth(UUID.fromString(USER_ID), 2026, 9)).thenReturn(emptySummary());

        VoiceTurnAnalysisResult result = resolver.resolve(
                USER_ID, SESSION_ID, "이번 달 공과금 알려줘",
                analysis(VoiceRequestedFunction.BILL_INQUIRY, Map.of()));

        assertEquals("2026-09에는 미납 고지서가 없습니다.", result.getTtsText());
        JsonNode items = result.getDisplayCard().path("items");
        assertTrue(items.isArray());
        assertEquals(0, items.size());
        assertEquals(0L, result.getDisplayCard().path("unpaidAmount").asLong());
    }

    @Test
    void returnsARetryGuideWhenTheMonthlyBillQueryFails() {
        when(billService.summarizeMonth(UUID.fromString(USER_ID), 2026, 9))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        VoiceTurnAnalysisResult result = resolver.resolve(
                USER_ID,
                SESSION_ID,
                "이번 달 공과금 알려줘",
                analysis(VoiceRequestedFunction.BILL_INQUIRY, Map.of()));

        assertEquals("고지서 정보를 불러오지 못했어요. 잠시 후 다시 말씀해 주세요.", result.getTtsText());
        assertEquals("BILL_QUERY_FAILED", result.getDisplayCard().path("type").asText());
        assertEquals(VoiceRequestedFunction.NONE, result.getRequestedFunction());
        assertEquals(VoiceNextAction.REASK_INPUT, result.getNextAction());
        assertEquals(DialogueStep.AWAITING_INPUT, result.getNextStep());
    }

    @Test
    void resolvesBillPaymentAsCameraNavigationWithoutCallingPaymentService() {
        VoiceTurnAnalysisResult result = resolver.resolve(
                USER_ID, SESSION_ID, "고지서를 촬영할게요",
                analysis(VoiceRequestedFunction.BILL_PAYMENT, Map.of()));

        assertEquals("고지서를 확인할 수 있도록 촬영 화면으로 이동할게요.", result.getTtsText());
        JsonNode card = result.getDisplayCard();
        assertEquals("BILL_PAYMENT_START", card.path("type").asText());
        assertEquals("BILL_CAMERA", card.path("screenCode").asText());
        assertEquals(SESSION_ID, card.path("params").path("voiceSessionId").asText());
        assertEquals("BILL_CAMERA", result.getSlots().get("screenCode"));
        assertFalse(result.getTtsText().contains("납부 완료"));
        assertTrue(result.getNextAction() == VoiceNextAction.PRESENT_RESULT);
        verify(billService, never()).execute(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private BillMonthlySummaryResponse emptySummary() {
        return new BillMonthlySummaryResponse("2026-09", 0L, 0L, 0L, 0L, 0L, 0L, false, List.of());
    }

    private VoiceTurnAnalysisResult analysis(VoiceRequestedFunction requestedFunction, Map<String, Object> slots) {
        return new VoiceTurnAnalysisResult(
                DialogueStep.AWAITING_INPUT,
                VoiceIntent.FINANCIAL_TASK,
                slots,
                BigDecimal.ONE,
                "요청하신 내용을 확인해 드릴게요.",
                "<speak>요청하신 내용을 확인해 드릴게요.</speak>",
                null,
                null,
                null,
                VoiceNextAction.PRESENT_RESULT,
                requestedFunction);
    }
}
