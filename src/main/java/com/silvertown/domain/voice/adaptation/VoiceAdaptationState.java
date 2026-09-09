package com.silvertown.domain.voice.adaptation;

import com.silvertown.domain.voice.enums.DialogueStep;
import com.silvertown.domain.voice.enums.GuidanceScope;
import com.silvertown.domain.voice.enums.VoiceGuidanceMode;
import java.util.Objects;

/** Short-lived, session-local state used to decide the next voice guidance rendering. */
public record VoiceAdaptationState(
        VoiceGuidanceMode mode,
        GuidanceScope scope,
        int supportSignalCount,
        DialogueStep currentDecisionStep,
        int successfulAdvanceStreak) {

    public VoiceAdaptationState {
        Objects.requireNonNull(mode, "mode must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(currentDecisionStep, "currentDecisionStep must not be null");
        if (supportSignalCount < 0) {
            throw new IllegalArgumentException("supportSignalCount must not be negative");
        }
        if (successfulAdvanceStreak < 0) {
            throw new IllegalArgumentException("successfulAdvanceStreak must not be negative");
        }
    }

    public static VoiceAdaptationState initial(DialogueStep currentDecisionStep) {
        return new VoiceAdaptationState(
                VoiceGuidanceMode.STANDARD,
                GuidanceScope.CURRENT_SESSION,
                0,
                currentDecisionStep,
                0);
    }

    public VoiceAdaptationState transition(
            VoiceGuidanceMode nextMode,
            GuidanceScope nextScope,
            int nextSupportSignalCount,
            DialogueStep nextDecisionStep,
            int nextSuccessfulAdvanceStreak) {
        return new VoiceAdaptationState(
                nextMode,
                nextScope,
                nextSupportSignalCount,
                nextDecisionStep,
                nextSuccessfulAdvanceStreak);
    }
}
