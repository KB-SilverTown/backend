package com.silvertown.domain.voice.enums;

/** Signals already identified by the voice flow that can change guidance rendering. */
public enum VoiceAdaptationSignal {
    SLOWER_REQUEST,
    FINANCIAL_RECONFIRMATION,
    LOW_STT_CONFIDENCE,
    REPLAY,
    FIRST_SILENCE,
    REPEATED_REASK,
    FASTER_REQUEST,
    NORMAL_ADVANCE,
    RESPONSE_RENDERED
}
