package com.silvertown.domain.voice.adaptation;

import com.silvertown.domain.voice.enums.DialogueStep;
import com.silvertown.domain.voice.enums.GuidanceScope;
import com.silvertown.domain.voice.enums.VoiceAdaptationSignal;
import com.silvertown.domain.voice.enums.VoiceGuidanceMode;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Applies the documented voice-guidance transition rules without reading or mutating any domain state.
 */
@Component
public class VoiceAdaptationPolicy {
    private static final BigDecimal SUPPORT_RATE_DELTA = new BigDecimal("-0.05");
    private static final BigDecimal STANDARD_RATE_DELTA = BigDecimal.ZERO;
    private static final BigDecimal COMPACT_RATE_DELTA = new BigDecimal("0.05");
    private static final BigDecimal MIN_SPEECH_RATE = new BigDecimal("0.90");
    private static final BigDecimal MAX_SPEECH_RATE = new BigDecimal("1.20");
    private static final int CURRENT_STEP_SUPPORT_SIGNAL_THRESHOLD = 2;
    private static final int SUPPORT_RELEASE_ADVANCE_THRESHOLD = 2;

    public VoiceAdaptationDecision decide(
            VoiceAdaptationState currentState,
            DialogueStep currentDecisionStep,
            VoiceAdaptationSignal signal,
            BigDecimal baseSpeechRate) {
        return decide(currentState, currentDecisionStep, Set.of(signal), baseSpeechRate);
    }

    public VoiceAdaptationDecision decide(
            VoiceAdaptationState currentState,
            DialogueStep currentDecisionStep,
            Set<VoiceAdaptationSignal> signals,
            BigDecimal baseSpeechRate) {
        Objects.requireNonNull(currentState, "currentState must not be null");
        Objects.requireNonNull(currentDecisionStep, "currentDecisionStep must not be null");
        Objects.requireNonNull(signals, "signals must not be null");
        Objects.requireNonNull(baseSpeechRate, "baseSpeechRate must not be null");

        EnumSet<VoiceAdaptationSignal> resolvedSignals = toEnumSet(signals);
        VoiceAdaptationState baseline = resetForNewDecisionStep(currentState, currentDecisionStep);
        VoiceAdaptationState nextState = applyHighestPrioritySignal(
                baseline, currentDecisionStep, resolvedSignals);
        BigDecimal rateDelta = rateDelta(nextState.mode());
        return new VoiceAdaptationDecision(
                nextState,
                rateDelta,
                clamp(baseSpeechRate.add(rateDelta), MIN_SPEECH_RATE, MAX_SPEECH_RATE));
    }

    private VoiceAdaptationState applyHighestPrioritySignal(
            VoiceAdaptationState state,
            DialogueStep currentDecisionStep,
            Set<VoiceAdaptationSignal> signals) {
        if (signals.contains(VoiceAdaptationSignal.SLOWER_REQUEST)) {
            return supportCurrentSession(state, currentDecisionStep);
        }
        if (signals.contains(VoiceAdaptationSignal.FINANCIAL_RECONFIRMATION)
                || signals.contains(VoiceAdaptationSignal.LOW_STT_CONFIDENCE)) {
            return supportCurrentStep(state, currentDecisionStep);
        }
        if (containsSupportSignal(signals)) {
            return supportFromBehaviorSignal(state, currentDecisionStep);
        }
        if (signals.contains(VoiceAdaptationSignal.FASTER_REQUEST)) {
            return compactCurrentSession(state, currentDecisionStep);
        }
        if (signals.contains(VoiceAdaptationSignal.RESPONSE_RENDERED)) {
            return consumeNextResponseSupport(state, currentDecisionStep);
        }
        if (signals.contains(VoiceAdaptationSignal.NORMAL_ADVANCE)) {
            return advanceNormally(state, currentDecisionStep);
        }
        return state;
    }

    private VoiceAdaptationState resetForNewDecisionStep(
            VoiceAdaptationState state, DialogueStep currentDecisionStep) {
        if (state.currentDecisionStep() == currentDecisionStep) {
            return state;
        }
        if (state.scope() == GuidanceScope.CURRENT_SESSION
                && state.mode() != VoiceGuidanceMode.STANDARD) {
            return state.transition(
                    state.mode(),
                    GuidanceScope.CURRENT_SESSION,
                    state.supportSignalCount(),
                    currentDecisionStep,
                    0);
        }
        return VoiceAdaptationState.initial(currentDecisionStep);
    }

    private VoiceAdaptationState supportCurrentSession(
            VoiceAdaptationState state, DialogueStep currentDecisionStep) {
        return state.transition(
                VoiceGuidanceMode.SUPPORT,
                GuidanceScope.CURRENT_SESSION,
                state.supportSignalCount(),
                currentDecisionStep,
                0);
    }

    private VoiceAdaptationState supportCurrentStep(
            VoiceAdaptationState state, DialogueStep currentDecisionStep) {
        return state.transition(
                VoiceGuidanceMode.SUPPORT,
                GuidanceScope.CURRENT_STEP,
                state.supportSignalCount(),
                currentDecisionStep,
                0);
    }

    private VoiceAdaptationState supportFromBehaviorSignal(
            VoiceAdaptationState state, DialogueStep currentDecisionStep) {
        int supportSignalCount = state.supportSignalCount() + 1;
        GuidanceScope scope = supportSignalCount >= CURRENT_STEP_SUPPORT_SIGNAL_THRESHOLD
                ? GuidanceScope.CURRENT_STEP
                : GuidanceScope.NEXT_RESPONSE;
        return state.transition(
                VoiceGuidanceMode.SUPPORT,
                scope,
                supportSignalCount,
                currentDecisionStep,
                0);
    }

    private VoiceAdaptationState compactCurrentSession(
            VoiceAdaptationState state, DialogueStep currentDecisionStep) {
        return state.transition(
                VoiceGuidanceMode.COMPACT,
                GuidanceScope.CURRENT_SESSION,
                state.supportSignalCount(),
                currentDecisionStep,
                0);
    }

    private VoiceAdaptationState consumeNextResponseSupport(
            VoiceAdaptationState state, DialogueStep currentDecisionStep) {
        if (state.mode() != VoiceGuidanceMode.SUPPORT
                || state.scope() != GuidanceScope.NEXT_RESPONSE) {
            return state;
        }
        return state.transition(
                VoiceGuidanceMode.STANDARD,
                GuidanceScope.CURRENT_SESSION,
                state.supportSignalCount(),
                currentDecisionStep,
                0);
    }

    private VoiceAdaptationState advanceNormally(
            VoiceAdaptationState state, DialogueStep currentDecisionStep) {
        if (state.mode() != VoiceGuidanceMode.SUPPORT
                || state.scope() != GuidanceScope.CURRENT_STEP) {
            return state;
        }

        int successfulAdvanceStreak = state.successfulAdvanceStreak() + 1;
        if (successfulAdvanceStreak < SUPPORT_RELEASE_ADVANCE_THRESHOLD) {
            return state.transition(
                    VoiceGuidanceMode.SUPPORT,
                    GuidanceScope.CURRENT_STEP,
                    state.supportSignalCount(),
                    currentDecisionStep,
                    successfulAdvanceStreak);
        }
        return state.transition(
                VoiceGuidanceMode.STANDARD,
                GuidanceScope.CURRENT_SESSION,
                state.supportSignalCount(),
                currentDecisionStep,
                0);
    }

    private boolean containsSupportSignal(Set<VoiceAdaptationSignal> signals) {
        return signals.contains(VoiceAdaptationSignal.REPLAY)
                || signals.contains(VoiceAdaptationSignal.FIRST_SILENCE)
                || signals.contains(VoiceAdaptationSignal.REPEATED_REASK);
    }

    private BigDecimal rateDelta(VoiceGuidanceMode mode) {
        return switch (mode) {
            case SUPPORT -> SUPPORT_RATE_DELTA;
            case COMPACT -> COMPACT_RATE_DELTA;
            case STANDARD -> STANDARD_RATE_DELTA;
        };
    }

    private BigDecimal clamp(BigDecimal value, BigDecimal minimum, BigDecimal maximum) {
        if (value.compareTo(minimum) < 0) {
            return minimum;
        }
        if (value.compareTo(maximum) > 0) {
            return maximum;
        }
        return value;
    }

    private EnumSet<VoiceAdaptationSignal> toEnumSet(Set<VoiceAdaptationSignal> signals) {
        if (signals.isEmpty()) {
            return EnumSet.noneOf(VoiceAdaptationSignal.class);
        }
        return EnumSet.copyOf(signals);
    }
}
