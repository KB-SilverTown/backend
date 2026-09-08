package com.silvertown.domain.risk.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.silvertown.domain.risk.dto.RiskFactorResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class RiskPolicyCalculatorTest {
    private final RiskPolicyCalculator calculator = new RiskPolicyCalculator();

    @Test
    void absoluteAmountScoreUsesApprovedBoundaries() {
        assertEquals(0, calculator.absoluteAmountScore(499_999L));
        assertEquals(5, calculator.absoluteAmountScore(500_000L));
        assertEquals(10, calculator.absoluteAmountScore(1_000_000L));
        assertEquals(15, calculator.absoluteAmountScore(3_000_000L));
        assertEquals(20, calculator.absoluteAmountScore(10_000_000L));
    }

    @Test
    void deviationScoreRequiresEnoughUserHistory() {
        List<Long> insufficientHistory = List.of(
                10_000L, 20_000L, 30_000L, 40_000L, 50_000L,
                60_000L, 70_000L, 80_000L, 90_000L);

        assertEquals(0, calculator.deviationScore(insufficientHistory, 10, 500_000L));
    }

    @Test
    void amountAtLeastFiveTimesMedianGetsHighestDeviationScore() {
        List<Long> history = List.of(
                40_000L, 45_000L, 45_000L, 50_000L, 50_000L,
                50_000L, 55_000L, 55_000L, 60_000L, 65_000L);

        assertEquals(15, calculator.deviationScore(history, 10, 250_000L));
    }

    @Test
    void familyWordAloneDoesNotCreateRiskFactor() {
        assertNull(calculator.conversationFactor("아들에게 생활비를 보내요"));
    }

    @Test
    void familyEmergencyAndDifferentAccountRequestCreatesRiskFactor() {
        RiskFactorResponse factor = calculator.conversationFactor(
                "아들이 휴대폰이 고장 났다며 친구 계좌로 보내 달래요");

        assertEquals("FAMILY_EMERGENCY_REQUEST", factor.getCode());
        assertEquals(25, factor.getWeight());
    }

    @Test
    void loanAdvancePaymentCreatesHighestConversationFactor() {
        RiskFactorResponse factor = calculator.conversationFactor(
                "대출을 받기 전에 수수료를 먼저 입금해야 한대요");

        assertEquals("LOAN_ADVANCE_PAYMENT", factor.getCode());
        assertEquals(40, factor.getWeight());
    }

    @Test
    void ordinaryLoanPaymentPlanWithoutRequestIsNotDetected() {
        assertNull(calculator.conversationFactor(
                "대출 받기 전에 수수료를 먼저 입금합니다"));
    }

    @Test
    void safeAccountTransferExpressionIsDetected() {
        RiskFactorResponse factor = calculator.conversationFactor(
                "안전계좌로 옮겨야 한다고 했어요");

        assertEquals("INSTITUTION_TRANSFER_REQUEST", factor.getCode());
    }

    @Test
    void multipleConversationReasonsAreReturnedButHighestCanBeSelected() {
        List<RiskFactorResponse> factors = calculator.conversationFactors(
                "경찰이 안전계좌로 지금 보내달라고 했고 안 보내면 계좌가 동결된대요");

        assertTrue(factors.stream()
                .anyMatch(factor -> "INSTITUTION_TRANSFER_REQUEST".equals(factor.getCode())));
        assertTrue(factors.stream()
                .anyMatch(factor -> "URGENCY_PRESSURE".equals(factor.getCode())));
        assertEquals(40, calculator.conversationFactor(
                "경찰이 안전계좌로 지금 보내달라고 했고 안 보내면 계좌가 동결된대요")
                .getWeight());
    }

    @Test
    void riskLevelUsesApprovedBoundaries() {
        assertEquals("LOW", calculator.level(29));
        assertEquals("MEDIUM", calculator.level(30));
        assertEquals("HIGH", calculator.level(60));
        assertEquals("CRITICAL", calculator.level(80));
    }
}
