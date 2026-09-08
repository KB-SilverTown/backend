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
    private static final String FRONTEND_ORIGIN = "http://localhost:5173";

    private final VoiceStreamWebSocketHandler voiceStreamWebSocketHandler;

    public VoiceStreamWebSocketConfig(VoiceStreamWebSocketHandler voiceStreamWebSocketHandler) {
        this.voiceStreamWebSocketHandler = voiceStreamWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(voiceStreamWebSocketHandler, "/api/voice/sessions/*/stream")
                .setAllowedOrigins(FRONTEND_ORIGIN);
    }
}
