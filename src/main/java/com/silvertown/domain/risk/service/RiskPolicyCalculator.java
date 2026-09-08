package com.silvertown.domain.risk.service;

import com.silvertown.domain.risk.dto.RiskFactorResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class RiskPolicyCalculator {
    public int absoluteAmountScore(long amount) {
        if (amount >= 10_000_000L) return 20;
        if (amount >= 3_000_000L) return 15;
        if (amount >= 1_000_000L) return 10;
        if (amount >= 500_000L) return 5;
        return 0;
    }

    public int deviationScore(List<Long> source, int minimumSamples, long currentAmount) {
        if (source == null || source.size() < minimumSamples) return 0;
        List<Long> amounts = source.stream().sorted(Comparator.naturalOrder()).toList();
        long median = percentile(amounts, 0.50);
        if (median > 0 && currentAmount / median >= 5) return 15;
        if (currentAmount > percentile(amounts, 0.97)) return 15;
        if (currentAmount > percentile(amounts, 0.90)) return 10;
        if (currentAmount > percentile(amounts, 0.75)) return 5;
        return 0;
    }

    public RiskFactorResponse conversationFactor(String answer) {
        return conversationFactors(answer).stream()
                .max(Comparator.comparingInt(RiskFactorResponse::getWeight))
                .orElse(null);
    }

    public List<RiskFactorResponse> conversationFactors(String answer) {
        if (answer == null || answer.isBlank()) return List.of();
        String text = answer.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        boolean requested = containsAny(
                text, "보내래", "보내달래", "보내라고", "보내야", "송금하래", "송금해야",
                "보내달라고", "이체하래", "이체해야", "옮기래", "옮겨야", "입금하래", "입금해야");
        boolean advance = containsAny(text, "먼저", "선입금", "받기전", "나오기전", "지급전");
        List<RiskFactorResponse> factors = new ArrayList<>();

        boolean institutionClaim = containsAny(
                text, "검찰", "경찰", "금감원", "금융감독원", "안전계좌");
        if (institutionClaim && requested) {
            factors.add(factor(
                    "INSTITUTION_TRANSFER_REQUEST", "기관 관계자가 송금을 요청한 상황", 40));
        }
        if (text.contains("대출") && advance && requested
                && containsAny(text, "입금", "송금", "수수료", "보증금", "보증료")) {
            factors.add(factor(
                    "LOAN_ADVANCE_PAYMENT", "대출 전 선입금을 요청받은 상황", 40));
        }
        if (containsAny(text, "수수료", "보증금", "보증료") && advance && requested) {
            factors.add(factor(
                    "ADVANCE_FEE_REQUEST", "서비스 제공 전 비용을 요청받은 상황", 30));
        }
        boolean family = containsAny(text, "아들", "딸", "손자", "손녀", "사위", "며느리");
        boolean familyRisk = containsAny(
                text, "휴대폰고장", "폰고장", "번호바뀜", "사고", "다쳤", "다른계좌", "친구계좌");
        if (family && familyRisk && requested) {
            factors.add(factor(
                    "FAMILY_EMERGENCY_REQUEST", "가족이 평소와 다른 방식으로 긴급 송금을 요청", 25));
        }
        boolean urgency = containsAny(text, "지금", "당장", "오늘안", "급하게");
        boolean consequence = containsAny(text, "안보내면", "큰일", "동결", "체포", "취소", "불이익");
        if (urgency && consequence && requested) {
            factors.add(factor(
                    "URGENCY_PRESSURE", "즉시 송금을 재촉하며 불이익을 강조", 25));
        }
        return List.copyOf(factors);
    }

    public String level(int score) {
        if (score >= 80) return "CRITICAL";
        if (score >= 60) return "HIGH";
        if (score >= 30) return "MEDIUM";
        return "LOW";
    }

    public String action(String level) {
        if ("HIGH".equals(level) || "CRITICAL".equals(level)) return "HOLD";
        if ("MEDIUM".equals(level)) return "ADDITIONAL_CHECK";
        return "NORMAL_CONFIRM";
    }

    private long percentile(List<Long> sorted, double ratio) {
        int index = (int) Math.ceil(sorted.size() * ratio) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }

    private RiskFactorResponse factor(String code, String label, int weight) {
        return new RiskFactorResponse(code, label, weight);
    }
}
