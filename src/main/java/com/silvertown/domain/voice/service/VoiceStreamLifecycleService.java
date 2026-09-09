package com.silvertown.domain.voice.service;

/**
 * Owns the persisted lifecycle of a transfer backend-stream voice turn.
 *
 * <p>The WebSocket transport calls this service in a follow-up integration change. Each mutation
 * is serialized by the owning voice-session row.
 */
public interface VoiceStreamLifecycleService {
    long claimInputTurn(String userId, String sessionId, String inputTurnId);

    void beginFinalProcessing(
            String userId, String sessionId, String inputTurnId, long lifecycleGeneration);

    void completeAiTurn(
            String userId,
            String sessionId,
            String inputTurnId,
            long lifecycleGeneration,
            String aiTurnId);

    void interruptAiTts(String userId, String sessionId, String interruptedAiTurnId);

    void cancelInputStream(
            String userId, String sessionId, String inputTurnId, long lifecycleGeneration);
}
