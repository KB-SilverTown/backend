package com.silvertown.domain.voice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.silvertown.domain.bill.dto.BillMonthlySummaryResponse;
import com.silvertown.domain.bill.dto.BillSummary;
import com.silvertown.domain.bill.service.BillService;
import com.silvertown.domain.voice.enums.DialogueStep;
import com.silvertown.domain.voice.enums.VoiceNextAction;
import com.silvertown.domain.voice.enums.VoiceRequestedFunction;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/** Resolves safe bill-domain results after a GENERAL_FINANCE voice classification. */
@Component
@RequiredArgsConstructor
public class BillVoiceResponseResolver {
    private static final String BILL_LIST_SCREEN = "BILL_LIST";
    private static final String BILL_CAMERA_SCREEN = "BILL_CAMERA";

    private final BillService billService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public VoiceTurnAnalysisResult resolve(
            String userId, String sessionId, String transcript, VoiceTurnAnalysisResult analysis) {
        if (analysis.getRequestedFunction() == VoiceRequestedFunction.BILL_INQUIRY) {
            return resolveBillInquiry(userId, transcript, analysis);
        }
        if (analysis.getRequestedFunction() == VoiceRequestedFunction.BILL_PAYMENT) {
            return resolveBillPayment(sessionId, analysis);
        }
        return analysis;
    }

    private VoiceTurnAnalysisResult resolveBillInquiry(
            String userId, String transcript, VoiceTurnAnalysisResult analysis) {
        Integer year = integerSlot(analysis.getSlots(), "year");
        Integer month = integerSlot(analysis.getSlots(), "month");
        YearMonth targetMonth = resolveTargetMonth(year, month, transcript);
        if (targetMonth == null) {
            return reaskMonth(analysis);
        }

        BillMonthlySummaryResponse summary;
        try {
            summary = billService.summarizeMonth(
                    UUID.fromString(userId), targetMonth.getYear(), targetMonth.getMonthValue());
        } catch (DataAccessException exception) {
            return queryFailed(analysis);
        }
        String ttsText = summary.getUnpaidCount() == 0
                ? summary.getYearMonth() + "에는 미납 고지서가 없습니다."
                : String.format(
                        Locale.KOREA,
                        "%s 미납 고지서는 %d건, 총 %,d원입니다.",
                        summary.getYearMonth(), summary.getUnpaidCount(), summary.getUnpaidAmount());
        ObjectNode displayCard = monthlySummaryCard(summary);
        Map<String, Object> slots = new LinkedHashMap<>(analysis.getSlots());
        slots.put("yearMonth", summary.getYearMonth());
        slots.put("unpaidCount", summary.getUnpaidCount());
        slots.put("unpaidAmount", summary.getUnpaidAmount());
        return resolved(analysis, slots, ttsText, displayCard);
    }

    private YearMonth resolveTargetMonth(Integer year, Integer month, String transcript) {
        if (year != null && month != null) {
            return yearMonthOrNull(year, month);
        }
        if (year != null) {
            return null;
        }
        String normalized = transcript == null ? "" : transcript.replaceAll("[\\s\\p{Punct}]", "");
        if (normalized.contains("지난달") || normalized.contains("저번달")) {
            return relativeMonthOrNull(month, YearMonth.now(clock).minusMonths(1));
        }
        if (normalized.contains("이번달") || normalized.contains("이달")) {
            return relativeMonthOrNull(month, YearMonth.now(clock));
        }
        return month == null ? YearMonth.now(clock) : null;
    }

    private YearMonth relativeMonthOrNull(Integer extractedMonth, YearMonth relativeMonth) {
        return (extractedMonth == null || extractedMonth == relativeMonth.getMonthValue())
                ? relativeMonth
                : null;
    }

    private YearMonth yearMonthOrNull(int year, int month) {
        if (year < 1000 || year > 9999) {
            return null;
        }
        try {
            return YearMonth.of(year, month);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private VoiceTurnAnalysisResult reaskMonth(VoiceTurnAnalysisResult analysis) {
        String ttsText = "조회할 연도와 월을 다시 말씀해 주세요.";
        ObjectNode displayCard = objectMapper.createObjectNode();
        displayCard.put("type", "BILL_PERIOD_REASK");
        return resolved(analysis, new LinkedHashMap<>(analysis.getSlots()), ttsText, displayCard);
    }

    private VoiceTurnAnalysisResult queryFailed(VoiceTurnAnalysisResult analysis) {
        String ttsText = "고지서 정보를 불러오지 못했어요. 잠시 후 다시 말씀해 주세요.";
        ObjectNode displayCard = objectMapper.createObjectNode();
        displayCard.put("type", "BILL_QUERY_FAILED");
        return new VoiceTurnAnalysisResult(
                DialogueStep.AWAITING_INPUT,
                analysis.getIntent(),
                new LinkedHashMap<>(analysis.getSlots()),
                analysis.getConfidence(),
                ttsText,
                "<speak>" + ttsText + "</speak>",
                displayCard,
                analysis.getRequiredSlot(),
                analysis.getDraftSummary(),
                VoiceNextAction.REASK_INPUT,
                VoiceRequestedFunction.NONE);
    }

    private VoiceTurnAnalysisResult resolveBillPayment(String sessionId, VoiceTurnAnalysisResult analysis) {
        String ttsText = "고지서를 확인할 수 있도록 촬영 화면으로 이동할게요.";
        ObjectNode displayCard = objectMapper.createObjectNode();
        displayCard.put("type", "BILL_PAYMENT_START");
        displayCard.put("screenCode", BILL_CAMERA_SCREEN);
        ObjectNode params = displayCard.putObject("params");
        params.put("voiceSessionId", sessionId);
        Map<String, Object> slots = new LinkedHashMap<>(analysis.getSlots());
        slots.put("screenCode", BILL_CAMERA_SCREEN);
        return resolved(analysis, slots, ttsText, displayCard);
    }

    private VoiceTurnAnalysisResult resolved(
            VoiceTurnAnalysisResult analysis,
            Map<String, Object> slots,
            String ttsText,
            JsonNode displayCard) {
        return new VoiceTurnAnalysisResult(
                analysis.getNextStep(),
                analysis.getIntent(),
                slots,
                analysis.getConfidence(),
                ttsText,
                "<speak>" + ttsText + "</speak>",
                displayCard,
                analysis.getRequiredSlot(),
                objectMapper.valueToTree(slots),
                analysis.getNextAction(),
                analysis.getRequestedFunction());
    }

    private ObjectNode monthlySummaryCard(BillMonthlySummaryResponse summary) {
        ObjectNode displayCard = objectMapper.createObjectNode();
        displayCard.put("type", "BILL_MONTHLY_SUMMARY");
        displayCard.put("screenCode", BILL_LIST_SCREEN);
        displayCard.put("yearMonth", summary.getYearMonth());
        displayCard.put("totalAmount", summary.getTotalAmount());
        displayCard.put("paidAmount", summary.getPaidAmount());
        displayCard.put("unpaidAmount", summary.getUnpaidAmount());
        displayCard.put("totalCount", summary.getTotalCount());
        displayCard.put("unpaidCount", summary.getUnpaidCount());
        displayCard.put("hasMoreItems", summary.isHasMoreItems());

        String[] yearMonth = summary.getYearMonth().split("-");
        ObjectNode params = displayCard.putObject("params");
        params.put("year", Integer.parseInt(yearMonth[0]));
        params.put("month", Integer.parseInt(yearMonth[1]));

        ArrayNode items = displayCard.putArray("items");
        for (BillSummary item : summary.getItems()) {
            ObjectNode node = items.addObject();
            node.put("billId", item.getBillId().toString());
            node.put("status", item.getStatus().name());
            node.put("payee", item.getPayee());
            if (item.getAmount() != null) {
                node.put("amount", item.getAmount());
            }
            if (item.getDueDate() != null) {
                node.put("dueDate", item.getDueDate().toString());
            }
            node.put("reconfirmRequired", item.isReconfirmRequired());
        }
        return displayCard;
    }

    private Integer integerSlot(Map<String, Object> slots, String name) {
        Object value = slots.get(name);
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Long && (Long) value >= Integer.MIN_VALUE && (Long) value <= Integer.MAX_VALUE) {
            return ((Long) value).intValue();
        }
        if (value instanceof BigDecimal && ((BigDecimal) value).scale() <= 0) {
            try {
                return ((BigDecimal) value).intValueExact();
            } catch (ArithmeticException ignored) {
                return null;
            }
        }
        if (value instanceof Number) {
            try {
                return new BigDecimal(value.toString()).intValueExact();
            } catch (NumberFormatException | ArithmeticException ignored) {
                return null;
            }
        }
        if (value instanceof String) {
            try {
                return new BigDecimal(((String) value).trim()).intValueExact();
            } catch (NumberFormatException | ArithmeticException ignored) {
                return null;
            }
        }
        return null;
    }
}
