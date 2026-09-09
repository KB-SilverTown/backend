package com.silvertown.domain.voice.adaptation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.silvertown.domain.voice.enums.DialogueStep;
import com.silvertown.domain.voice.enums.GuidanceScope;
import com.silvertown.domain.voice.enums.VoiceAdaptationSignal;
import com.silvertown.domain.voice.enums.VoiceGuidanceMode;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.Test;

class VoiceAdaptationPolicyTest {
    private static final DialogueStep STEP = DialogueStep.AWAITING_AMOUNT;
    private static final BigDecimal DEFAULT_RATE = new BigDecimal("1.05");

    private final VoiceAdaptationPolicy policy = new VoiceAdaptationPolicy();

    @Test
    void calculatesModeRateDeltasFromDefaultRate() {
        VoiceAdaptationState initial = VoiceAdaptationState.initial(STEP);

        assertDecision(
                policy.decide(initial, STEP, Set.of(), DEFAULT_RATE),
                VoiceGuidanceMode.STANDARD,
                GuidanceScope.CURRENT_SESSION,
                "0",
                "1.05");
        assertDecision(
                policy.decide(initial, STEP, VoiceAdaptationSignal.REPLAY, DEFAULT_RATE),
                VoiceGuidanceMode.SUPPORT,
                GuidanceScope.NEXT_RESPONSE,
                "-0.05",
                "1.00");
        assertDecision(
                policy.decide(initial, STEP, VoiceAdaptationSignal.FASTER_REQUEST, DEFAULT_RATE),
                VoiceGuidanceMode.COMPACT,
                GuidanceScope.CURRENT_SESSION,
                "0.05",
                "1.10");
    }

    @Test
    void clampsEffectiveSpeechRateAtBothBoundaries() {
        VoiceAdaptationState initial = VoiceAdaptationState.initial(STEP);

        assertEquals(
                new BigDecimal("0.90"),
                policy.decide(initial, STEP, VoiceAdaptationSignal.REPLAY, new BigDecimal("0.90"))
                        .effectiveSpeechRate());
        assertEquals(
                new BigDecimal("1.20"),
                policy.decide(initial, STEP, VoiceAdaptationSignal.FASTER_REQUEST, new BigDecimal("1.20"))
                        .effectiveSpeechRate());
    }

    @Test
    void firstReplaySupportsOnlyTheNextNewResponse() {
        VoiceAdaptationDecision replay = policy.decide(
                VoiceAdaptationState.initial(STEP), STEP, VoiceAdaptationSignal.REPLAY, DEFAULT_RATE);

        assertDecision(
                replay,
                VoiceGuidanceMode.SUPPORT,
                GuidanceScope.NEXT_RESPONSE,
                "-0.05",
                "1.00");
        assertDecision(
                policy.decide(
                        replay.nextState(), STEP, VoiceAdaptationSignal.RESPONSE_RENDERED, DEFAULT_RATE),
                VoiceGuidanceMode.STANDARD,
                GuidanceScope.CURRENT_SESSION,
                "0",
                "1.05");
    }

    @Test
    void secondReplayInTheSameDecisionStepSupportsThatStep() {
        VoiceAdaptationDecision firstReplay = policy.decide(
                VoiceAdaptationState.initial(STEP), STEP, VoiceAdaptationSignal.REPLAY, DEFAULT_RATE);

        VoiceAdaptationDecision secondReplay = policy.decide(
                firstReplay.nextState(), STEP, VoiceAdaptationSignal.REPLAY, DEFAULT_RATE);

        assertDecision(
                secondReplay,
                VoiceGuidanceMode.SUPPORT,
                GuidanceScope.CURRENT_STEP,
                "-0.05",
                "1.00");
        assertEquals(2, secondReplay.nextState().supportSignalCount());
    }

    @Test
    void firstSilenceAndRepeatedReaskAreBehaviorSupportSignals() {
        VoiceAdaptationState initial = VoiceAdaptationState.initial(STEP);

        assertDecision(
                policy.decide(initial, STEP, VoiceAdaptationSignal.FIRST_SILENCE, DEFAULT_RATE),
                VoiceGuidanceMode.SUPPORT,
                GuidanceScope.NEXT_RESPONSE,
                "-0.05",
                "1.00");
        assertDecision(
                policy.decide(initial, STEP, VoiceAdaptationSignal.REPEATED_REASK, DEFAULT_RATE),
                VoiceGuidanceMode.SUPPORT,
                GuidanceScope.NEXT_RESPONSE,
                "-0.05",
                "1.00");
    }

    @Test
    void twoNormalAdvancesReleaseCurrentStepSupport() {
        VoiceAdaptationState supportCurrentStep = new VoiceAdaptationState(
                VoiceGuidanceMode.SUPPORT, GuidanceScope.CURRENT_STEP, 2, STEP, 0);

        VoiceAdaptationDecision firstAdvance = policy.decide(
                supportCurrentStep, STEP, VoiceAdaptationSignal.NORMAL_ADVANCE, DEFAULT_RATE);
        assertDecision(
                firstAdvance,
                VoiceGuidanceMode.SUPPORT,
                GuidanceScope.CURRENT_STEP,
                "-0.05",
                "1.00");
        assertEquals(1, firstAdvance.nextState().successfulAdvanceStreak());

        assertDecision(
                policy.decide(
                        firstAdvance.nextState(), STEP, VoiceAdaptationSignal.NORMAL_ADVANCE, DEFAULT_RATE),
                VoiceGuidanceMode.STANDARD,
                GuidanceScope.CURRENT_SESSION,
                "0",
                "1.05");
    }

    @Test
    void slowerAndFasterRequestsUseTheirDocumentedSessionScopes() {
        VoiceAdaptationState initial = VoiceAdaptationState.initial(STEP);

        assertDecision(
                policy.decide(initial, STEP, VoiceAdaptationSignal.SLOWER_REQUEST, DEFAULT_RATE),
                VoiceGuidanceMode.SUPPORT,
                GuidanceScope.CURRENT_SESSION,
                "-0.05",
                "1.00");
        assertDecision(
                policy.decide(initial, STEP, VoiceAdaptationSignal.FASTER_REQUEST, DEFAULT_RATE),
                VoiceGuidanceMode.COMPACT,
                GuidanceScope.CURRENT_SESSION,
                "0.05",
                "1.10");
    }

    @Test
    void signalPriorityPrefersSlowerThenFinancialReconfirmationThenLowConfidence() {
        VoiceAdaptationState initial = VoiceAdaptationState.initial(STEP);

        assertDecision(
                policy.decide(
                        initial,
                        STEP,
                        Set.of(
                                VoiceAdaptationSignal.FASTER_REQUEST,
                                VoiceAdaptationSignal.LOW_STT_CONFIDENCE),
                        DEFAULT_RATE),
                VoiceGuidanceMode.SUPPORT,
                GuidanceScope.CURRENT_STEP,
                "-0.05",
                "1.00");
        assertDecision(
                policy.decide(
                        initial,
                        STEP,
                        Set.of(
                                VoiceAdaptationSignal.FASTER_REQUEST,
                                VoiceAdaptationSignal.FINANCIAL_RECONFIRMATION),
                        DEFAULT_RATE),
                VoiceGuidanceMode.SUPPORT,
                GuidanceScope.CURRENT_STEP,
                "-0.05",
                "1.00");
        assertDecision(
                policy.decide(
                        initial,
                        STEP,
                        Set.of(
                                VoiceAdaptationSignal.SLOWER_REQUEST,
                                VoiceAdaptationSignal.FINANCIAL_RECONFIRMATION),
                        DEFAULT_RATE),
                VoiceGuidanceMode.SUPPORT,
                GuidanceScope.CURRENT_SESSION,
                "-0.05",
                "1.00");
    }

    @Test
    void newDecisionStepReleasesCurrentStepSupportButKeepsCompactSession() {
        VoiceAdaptationState supportCurrentStep = new VoiceAdaptationState(
                VoiceGuidanceMode.SUPPORT, GuidanceScope.CURRENT_STEP, 2, STEP, 0);
        DialogueStep nextStep = DialogueStep.WAITING_FINAL_APPROVAL;

        assertDecision(
                policy.decide(supportCurrentStep, nextStep, Set.of(), DEFAULT_RATE),
                VoiceGuidanceMode.STANDARD,
                GuidanceScope.CURRENT_SESSION,
                "0",
                "1.05");

        VoiceAdaptationState compact = new VoiceAdaptationState(
                VoiceGuidanceMode.COMPACT, GuidanceScope.CURRENT_SESSION, 0, STEP, 0);
        assertDecision(
                policy.decide(compact, nextStep, Set.of(), DEFAULT_RATE),
                VoiceGuidanceMode.COMPACT,
                GuidanceScope.CURRENT_SESSION,
                "0.05",
                "1.10");
    }

    @Test
    void supportCurrentSessionPersistsAcrossDecisionStepTransition() {
        VoiceAdaptationState supportCurrentSession = new VoiceAdaptationState(
                VoiceGuidanceMode.SUPPORT, GuidanceScope.CURRENT_SESSION, 0, STEP, 0);
        DialogueStep nextStep = DialogueStep.WAITING_FINAL_APPROVAL;

        VoiceAdaptationDecision decision = policy.decide(
                supportCurrentSession, nextStep, Set.of(), DEFAULT_RATE);

        assertDecision(
                decision,
                VoiceGuidanceMode.SUPPORT,
                GuidanceScope.CURRENT_SESSION,
                "-0.05",
                "1.00");
        assertEquals(nextStep, decision.nextState().currentDecisionStep());
    }

    private void assertDecision(
            VoiceAdaptationDecision decision,
            VoiceGuidanceMode expectedMode,
            GuidanceScope expectedScope,
            String expectedDelta,
            String expectedRate) {
        assertEquals(expectedMode, decision.guidanceMode());
        assertEquals(expectedScope, decision.guidanceScope());
        assertEquals(new BigDecimal(expectedDelta), decision.speechRateDelta());
        assertEquals(new BigDecimal(expectedRate), decision.effectiveSpeechRate());
    }
}
