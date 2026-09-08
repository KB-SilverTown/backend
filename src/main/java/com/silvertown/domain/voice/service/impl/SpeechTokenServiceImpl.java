package com.silvertown.domain.voice.service.impl;

import com.silvertown.domain.voice.client.AzureSpeechTokenClient;
import com.silvertown.domain.voice.dto.SpeechTokenResponse;
import com.silvertown.domain.voice.service.SpeechTokenService;
import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SpeechTokenServiceImpl implements SpeechTokenService {

    private static final long MAX_TOKEN_TTL_SECONDS = 540;

    private final AzureSpeechTokenClient azureSpeechTokenClient;
    private final Clock clock;
    private final String region;
    private final long tokenTtlSeconds;

    public SpeechTokenServiceImpl(
            AzureSpeechTokenClient azureSpeechTokenClient,
            Clock clock,
            @Value("${azure.speech.region:}") String region,
            @Value("${azure.speech.token-ttl-seconds:540}") long tokenTtlSeconds
    ) {
        this.azureSpeechTokenClient = azureSpeechTokenClient;
        this.clock = clock;
        this.region = region;
        this.tokenTtlSeconds = tokenTtlSeconds;
    }

    @Override
    public SpeechTokenResponse issueToken() {
        if (region == null || region.trim().isEmpty()
                || tokenTtlSeconds <= 0 || tokenTtlSeconds > MAX_TOKEN_TTL_SECONDS) {
            throw new BusinessException(ErrorCode.SPEECH_NOT_CONFIGURED);
        }

        return new SpeechTokenResponse(
                azureSpeechTokenClient.issueToken(),
                region,
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).plusSeconds(tokenTtlSeconds)
        );
    }
}
