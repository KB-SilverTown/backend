package com.silvertown.global.config;

import com.silvertown.domain.voice.websocket.VoiceStreamWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** Registers the documented transfer audio stream endpoint without widening it to other flows. */
@Configuration
@EnableWebSocket
public class VoiceStreamWebSocketConfig implements WebSocketConfigurer {
    private static final String[] FRONTEND_ORIGIN_PATTERNS = {
            "http://localhost",
            "http://localhost:*",
            "https://localhost",
            "https://localhost:*",
            "capacitor://localhost"
    };

    private final VoiceStreamWebSocketHandler voiceStreamWebSocketHandler;

    public VoiceStreamWebSocketConfig(VoiceStreamWebSocketHandler voiceStreamWebSocketHandler) {
        this.voiceStreamWebSocketHandler = voiceStreamWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(voiceStreamWebSocketHandler, "/api/voice/sessions/*/stream")
                .setAllowedOriginPatterns(FRONTEND_ORIGIN_PATTERNS);
    }
}
