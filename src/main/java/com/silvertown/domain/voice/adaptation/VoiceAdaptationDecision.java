package com.silvertown.domain.voice.adaptation;

import com.silvertown.domain.voice.enums.GuidanceScope;
import com.silvertown.domain.voice.enums.VoiceGuidanceMode;
import java.math.BigDecimal;
import java.util.Objects;

/** Deterministic result of applying one or more adaptation signals to a session state. */
public record VoiceAdaptationDecision(
        VoiceAdaptationState nextState,
        BigDecimal speechRateDelta,
        BigDecimal effectiveSpeechRate) {

    public VoiceAdaptationDecision {
        Objects.requireNonNull(nextState, "nextState must not be null");
        Objects.requireNonNull(speechRateDelta, "speechRateDelta must not be null");
        Objects.requireNonNull(effectiveSpeechRate, "effectiveSpeechRate must not be null");
    }

    public VoiceGuidanceMode guidanceMode() {
        return nextState.mode();
    }

    public GuidanceScope guidanceScope() {
        return nextState.scope();
    }
}
