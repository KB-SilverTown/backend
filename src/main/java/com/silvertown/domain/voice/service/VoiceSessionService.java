package com.silvertown.domain.voice.service;

import com.silvertown.domain.voice.dto.VoiceSessionCreateRequest;
import com.silvertown.domain.voice.dto.VoiceSessionDetailResponse;
import com.silvertown.domain.voice.dto.VoiceSessionResponse;

public interface VoiceSessionService {
    VoiceSessionResponse create(String userId, VoiceSessionCreateRequest request);

    VoiceSessionDetailResponse get(String userId, String sessionId);

    VoiceSessionDetailResponse close(String userId, String sessionId);
}
