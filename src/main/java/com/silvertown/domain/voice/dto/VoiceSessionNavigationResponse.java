package com.silvertown.domain.voice.dto;

import com.silvertown.domain.voice.enums.VoiceSessionNavigationScreenCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class VoiceSessionNavigationResponse {
    private final VoiceSessionNavigationScreenCode screenCode;
    private final String voiceSessionId;
}
