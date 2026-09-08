package com.silvertown.domain.voice.service;

import com.silvertown.domain.voice.dto.VoiceSettingsRequest;
import com.silvertown.domain.voice.dto.VoiceSettingsResponse;

public interface VoiceSettingsService {
    VoiceSettingsResponse get(String userId);

    VoiceSettingsResponse update(String userId, VoiceSettingsRequest request);
}
