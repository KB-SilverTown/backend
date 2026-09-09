package com.silvertown.domain.voice.websocket;

import java.util.List;

public final class VoiceStreamWebSocketPolicy {
    public static final List<String> FRONTEND_ORIGIN_PATTERNS = List.of(
            "http://localhost",
            "http://localhost:*",
            "https://localhost",
            "https://localhost:*",
            "capacitor://localhost");

    private VoiceStreamWebSocketPolicy() {}
}
