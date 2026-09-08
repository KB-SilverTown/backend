package com.silvertown.domain.voice.dto;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SpeechTokenResponse {
    private final String token;
    private final String region;
    private final OffsetDateTime expiresAt;
}
