package com.silvertown.domain.voice.service;

import com.silvertown.domain.voice.dto.VoiceSessionEventRequest;
import com.silvertown.domain.voice.dto.VoiceSessionEventResponse;

public interface VoiceSessionEventService {
    VoiceSessionEventResponse handle(String userId, String sessionId, VoiceSessionEventRequest request);
}
