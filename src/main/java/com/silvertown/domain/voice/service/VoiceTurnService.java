package com.silvertown.domain.voice.service;

import com.silvertown.domain.voice.dto.VoiceTurnRequest;
import com.silvertown.domain.voice.dto.VoiceTurnResponse;
import com.silvertown.domain.voice.stt.AzureSpeechDetailedResult;

public interface VoiceTurnService {
    VoiceTurnResponse process(String userId, String sessionId, VoiceTurnRequest request);

    /** Retained for non-stream callers during the WebSocket lifecycle migration. */
    VoiceTurnResponse processAzureTransferFinal(
            String userId, String sessionId, String turnId, AzureSpeechDetailedResult result);

    VoiceTurnResponse processAzureTransferFinal(
            String userId,
            String sessionId,
            String inputTurnId,
            long lifecycleGeneration,
            AzureSpeechDetailedResult result);
}
