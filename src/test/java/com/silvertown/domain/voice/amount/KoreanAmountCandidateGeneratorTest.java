package com.silvertown.domain.voice.amount;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.silvertown.domain.voice.stt.AzureSpeechDetailedResult;
import com.silvertown.domain.voice.stt.AzureSpeechNBestAlternative;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class KoreanAmountCandidateGeneratorTest {
    private final KoreanAmountCandidateGenerator generator = new KoreanAmountCandidateGenerator();

    @Test
    void normalizesArabicAndKoreanWonExpressionsToTheSameCandidate() {
        for (String transcript : List.of("오만 원 보내줘", "5만 원 보내줘", "50,000원 보내줘")) {
            AmountCandidateDecision decision = generator.decide(result(transcript, "0.95"));

            assertEquals(AmountCandidateDecisionType.CONFIRMED, decision.type());
            assertEquals(50_000L, decision.recognizedAmount());
            assertEquals(List.of(50_000L), decision.candidates());
        }
    }

    @Test
    void removesDuplicateAmountsAcrossAzureNBest() {
        AmountCandidateDecision decision = generator.decide(new AzureSpeechDetailedResult(
                "오만 원 보내줘",
                new BigDecimal("0.95"),
                List.of(
                        new AzureSpeechNBestAlternative("5만 원 보내줘", new BigDecimal("0.90")),
                        new AzureSpeechNBestAlternative("50,000원 보내줘", new BigDecimal("0.85")))));

        assertEquals(AmountCandidateDecisionType.CONFIRMED, decision.type());
        assertEquals(List.of(50_000L), decision.candidates());
    }

    @Test
    void makesSameUnitConfusionCandidateAndRequiresReconfirmation() {
        AmountCandidateDecision decision = generator.decide(result("삼십만 원 보내줘", "0.95"));

        assertEquals(AmountCandidateDecisionType.RECONFIRM, decision.type());
        assertNull(decision.recognizedAmount());
        assertEquals(List.of(300_000L, 400_000L), decision.candidates());
        assertTrue(decision.confusionPairApplied());
    }

    @Test
    void doesNotCreateConfusionCandidateByChangingTheAmountUnit() {
        AmountCandidateDecision decision = generator.decide(result("십만 원 보내줘", "0.95"));

        assertEquals(AmountCandidateDecisionType.CONFIRMED, decision.type());
        assertEquals(List.of(100_000L), decision.candidates());
        assertFalse(decision.confusionPairApplied());
    }

    @Test
    void reasksForLowConfidenceNoCandidateOrMoreThanThreeCandidates() {
        AmountCandidateDecision lowConfidence = generator.decide(result("오만 원 보내줘", "0.69"));
        AmountCandidateDecision noCandidate = generator.decide(result("그분에게 보내줘", "0.95"));
        AmountCandidateDecision tooManyCandidates = generator.decide(new AzureSpeechDetailedResult(
                "일만 원 보내줘",
                new BigDecimal("0.95"),
                List.of(
                        new AzureSpeechNBestAlternative("이만 원 보내줘", new BigDecimal("0.90")),
                        new AzureSpeechNBestAlternative("오만 원 보내줘", new BigDecimal("0.85")),
                        new AzureSpeechNBestAlternative("육만 원 보내줘", new BigDecimal("0.80")))));

        assertEquals(AmountCandidateDecisionType.REASK, lowConfidence.type());
        assertEquals(AmountCandidateDecisionType.REASK, noCandidate.type());
        assertEquals(AmountCandidateDecisionType.REASK, tooManyCandidates.type());
        assertEquals(4, tooManyCandidates.candidates().size());
    }

    @Test
    void rejectsZeroNegativeDecimalUnitlessAndMultipleAmounts() {
        for (String transcript : List.of(
                "0원 보내줘", "-5만 원 보내줘", "- 5만 원 보내줘", "1.5만 원 보내줘",
                "오만 보내줘", "오만 원과 육만 원 보내줘")) {
            assertEquals(AmountCandidateDecisionType.REASK, generator.decide(result(transcript, "0.95")).type());
        }
    }

    @Test
    void reasksForDuplicateOrNonDecreasingLargeUnits() {
        for (String transcript : List.of("만억 원 보내줘", "만만 원 보내줘")) {
            assertEquals(AmountCandidateDecisionType.REASK, generator.decide(result(transcript, "0.95")).type());
        }
    }

    private AzureSpeechDetailedResult result(String transcript, String confidence) {
        return new AzureSpeechDetailedResult(transcript, new BigDecimal(confidence), List.of());
    }
}
