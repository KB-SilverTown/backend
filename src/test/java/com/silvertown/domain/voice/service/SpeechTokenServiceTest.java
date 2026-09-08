package com.silvertown.domain.voice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.silvertown.domain.voice.client.AzureSpeechTokenClient;
import com.silvertown.domain.voice.dto.SpeechTokenResponse;
import com.silvertown.domain.voice.service.impl.SpeechTokenServiceImpl;
import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SpeechTokenServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-02T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void returnsTokenRegionAndExpirationInUtc() {
        AzureSpeechTokenClient client = Mockito.mock(AzureSpeechTokenClient.class);
        Mockito.when(client.issueToken()).thenReturn("short-lived-token");
        SpeechTokenService service = new SpeechTokenServiceImpl(client, clock, "koreacentral", 540);

        SpeechTokenResponse response = service.issueToken();

        assertEquals("short-lived-token", response.getToken());
        assertEquals("koreacentral", response.getRegion());
        assertEquals("2026-09-02T00:09Z", response.getExpiresAt().toString());
    }

    @Test
    void rejectsTtlBeyondAzureTokenSafetyWindow() {
        AzureSpeechTokenClient client = Mockito.mock(AzureSpeechTokenClient.class);
        SpeechTokenService service = new SpeechTokenServiceImpl(client, clock, "koreacentral", 541);

        BusinessException exception = assertThrows(BusinessException.class, service::issueToken);

        assertEquals(ErrorCode.SPEECH_NOT_CONFIGURED, exception.getErrorCode());
    }

    @Test
    void rejectsBlankRegion() {
        AzureSpeechTokenClient client = Mockito.mock(AzureSpeechTokenClient.class);
        SpeechTokenService service = new SpeechTokenServiceImpl(client, clock, " ", 540);

        BusinessException exception = assertThrows(BusinessException.class, service::issueToken);

        assertEquals(ErrorCode.SPEECH_NOT_CONFIGURED, exception.getErrorCode());
        Mockito.verifyNoInteractions(client);
    }
}
