package com.silvertown.domain.voice.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import org.junit.jupiter.api.Test;

class SpeechTokenLiveSmokeTestTargetValidationTest {

    @Test
    void allowsDefaultLoopbackHttpTarget() {
        assertEquals("http://localhost:8080",
                SpeechTokenLiveSmokeTest.normalizeBaseUrl("localhost:8080"));
    }

    @Test
    void allowsRemoteHttpsTarget() {
        assertEquals("https://speech-smoke.example.com",
                SpeechTokenLiveSmokeTest.normalizeBaseUrl("https://speech-smoke.example.com/"));
    }

    @Test
    void rejectsRemoteHttpTarget() {
        assertThrows(IllegalArgumentException.class,
                () -> SpeechTokenLiveSmokeTest.normalizeBaseUrl("http://speech-smoke.example.com"));
    }

    @Test
    void rejectsUnsupportedProtocolForLoopbackTarget() {
        assertThrows(IllegalArgumentException.class,
                () -> SpeechTokenLiveSmokeTest.normalizeBaseUrl("ftp://localhost:8080"));
    }

    @Test
    void selectsNoProxyClientForLoopbackAndDefaultClientForRemoteHttps() {
        assertNotSame(
                SpeechTokenLiveSmokeTest.selectHttpClient(URI.create("http://localhost:8080")),
                SpeechTokenLiveSmokeTest.selectHttpClient(URI.create("https://speech-smoke.example.com")));
    }
}
