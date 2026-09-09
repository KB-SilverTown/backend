package com.silvertown.domain.voice.adaptation;

import com.silvertown.domain.voice.enums.VoiceAdaptationSignal;

/** Explicit, whitelisted voice-guidance commands that must not enter financial intent analysis. */
public enum VoiceGuidanceCommand {
    SLOWER(VoiceAdaptationSignal.SLOWER_REQUEST),
    FASTER(VoiceAdaptationSignal.FASTER_REQUEST),
    DEFAULT_SPEED(VoiceAdaptationSignal.DEFAULT_SPEED_REQUEST),
    LOUDER(null),
    QUIETER(null),
    DEFAULT_VOLUME(null);

    private final VoiceAdaptationSignal adaptationSignal;

    VoiceGuidanceCommand(VoiceAdaptationSignal adaptationSignal) {
        this.adaptationSignal = adaptationSignal;
    }

    public VoiceAdaptationSignal adaptationSignal() {
        return adaptationSignal;
    }
}
