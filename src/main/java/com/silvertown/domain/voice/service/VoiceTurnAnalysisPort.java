package com.silvertown.domain.voice.service;

/**
 * Boundary for turning a final STT transcript into a validated dialogue response draft.
 * Implementations may use an LLM, but must not execute financial operations directly.
 */
public interface VoiceTurnAnalysisPort {
    VoiceTurnAnalysisResult analyze(VoiceTurnAnalysisCommand command);
}
