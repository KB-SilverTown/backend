package com.silvertown.domain.voice.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class VoiceStreamWebSocketPolicyTest {

    @Test
    void acceptsOnlyTheConfirmedExplicitFrontendOrigins() {
        VoiceStreamWebSocketPolicy policy =
                new VoiceStreamWebSocketPolicy("http://localhost:5173, https://localhost");

        assertEquals(List.of("http://localhost:5173", "https://localhost"), policy.allowedOrigins());
    }

    @Test
    void rejectsWildcardOrigins() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new VoiceStreamWebSocketPolicy("http://localhost:*,https://*.example.com"));
    }
}
