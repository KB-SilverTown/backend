package com.silvertown.domain.voice.service;

import com.silvertown.domain.voice.dto.VoiceUiActionRequest;
import com.silvertown.domain.voice.dto.VoiceUiActionResponse;

public interface VoiceUiActionService {
    VoiceUiActionResponse process(String userId, String sessionId, VoiceUiActionRequest request);
}
