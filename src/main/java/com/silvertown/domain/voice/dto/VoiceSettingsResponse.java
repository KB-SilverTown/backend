package com.silvertown.domain.voice.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class VoiceSettingsResponse {
    private final String ttsVoice;
    private final BigDecimal speechRateMultiplier;
    private final BigDecimal pitchMultiplier;
    private final BigDecimal volumeMultiplier;
    private final OffsetDateTime updatedAt;
}
