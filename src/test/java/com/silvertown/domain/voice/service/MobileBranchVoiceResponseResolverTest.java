package com.silvertown.domain.voice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvertown.domain.voice.enums.DialogueStep;
import com.silvertown.domain.voice.enums.VoiceIntent;
import com.silvertown.domain.voice.enums.VoiceNextAction;
import com.silvertown.domain.voice.enums.VoiceRequestedFunction;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MobileBranchVoiceResponseResolverTest {

    private final MobileBranchVoiceResponseResolver resolver =
            new MobileBranchVoiceResponseResolver(new ObjectMapper());

    @Test
    void requestsLocationAndProvidesTheNearbyScreenContract() {
        VoiceTurnAnalysisResult result = resolver.resolve(mobileBranchAnalysis());

        assertEquals(VoiceRequestedFunction.MOBILE_BRANCH_RECOMMEND, result.getRequestedFunction());
        assertEquals(VoiceNextAction.PRESENT_RESULT, result.getNextAction());
        assertEquals(DialogueStep.AWAITING_INPUT, result.getNextStep());
        assertTrue((Boolean) result.getSlots().get("locationRequired"));
        assertEquals("MOBILE_BRANCH_LOCATION_REQUIRED", result.getDisplayCard().path("type").asText());
        assertEquals("MOBILE_BRANCH_NEARBY", result.getDisplayCard().path("screenCode").asText());
        assertEquals("/api/mobile-branches/nearby", result.getDisplayCard().path("apiPath").asText());
        assertEquals("latitude", result.getDisplayCard().path("requiredParams").get(0).asText());
        assertEquals("longitude", result.getDisplayCard().path("requiredParams").get(1).asText());
        assertTrue(result.getDisplayCard().path("actions").isArray());
    }

    @Test
    void leavesOtherGeneralFinanceResultsUntouched() {
        VoiceTurnAnalysisResult accountInquiry = new VoiceTurnAnalysisResult(
                DialogueStep.AWAITING_INPUT,
                VoiceIntent.ACCOUNT_INQUIRY,
                Map.of(),
                BigDecimal.ONE,
                "계좌를 확인해 드릴게요.",
                "<speak>계좌를 확인해 드릴게요.</speak>",
                null,
                null,
                null,
                VoiceNextAction.PRESENT_RESULT,
                VoiceRequestedFunction.ACCOUNT_INQUIRY);

        assertSame(accountInquiry, resolver.resolve(accountInquiry));
    }

    private VoiceTurnAnalysisResult mobileBranchAnalysis() {
        return new VoiceTurnAnalysisResult(
                DialogueStep.AWAITING_INPUT,
                VoiceIntent.MOBILE_BRANCH,
                Map.of("query", "가까운 이동점포"),
                new BigDecimal("0.95"),
                "이동점포를 확인해 드릴게요.",
                "<speak>이동점포를 확인해 드릴게요.</speak>",
                null,
                null,
                null,
                VoiceNextAction.PRESENT_RESULT,
                VoiceRequestedFunction.MOBILE_BRANCH_RECOMMEND);
    }
}
