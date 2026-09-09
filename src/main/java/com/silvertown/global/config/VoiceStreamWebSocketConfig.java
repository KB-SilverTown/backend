package com.silvertown.global.config;

import com.silvertown.domain.voice.websocket.VoiceStreamWebSocketHandler;
import com.silvertown.domain.voice.websocket.VoiceStreamHandshakeHandler;
import com.silvertown.domain.voice.websocket.VoiceStreamHandshakeInterceptor;
import com.silvertown.domain.voice.websocket.VoiceStreamWebSocketPolicy;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** Registers the documented transfer audio stream endpoint without widening it to other flows. */
@Configuration
@EnableWebSocket
public class VoiceStreamWebSocketConfig implements WebSocketConfigurer {
    private final VoiceStreamWebSocketHandler voiceStreamWebSocketHandler;
    private final VoiceStreamHandshakeInterceptor voiceStreamHandshakeInterceptor;
    private final VoiceStreamHandshakeHandler voiceStreamHandshakeHandler;

    public VoiceStreamWebSocketConfig(
            VoiceStreamWebSocketHandler voiceStreamWebSocketHandler,
            VoiceStreamHandshakeInterceptor voiceStreamHandshakeInterceptor,
            VoiceStreamHandshakeHandler voiceStreamHandshakeHandler) {
        this.voiceStreamWebSocketHandler = voiceStreamWebSocketHandler;
        this.voiceStreamHandshakeInterceptor = voiceStreamHandshakeInterceptor;
        this.voiceStreamHandshakeHandler = voiceStreamHandshakeHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(voiceStreamWebSocketHandler, "/api/voice/sessions/*/stream")
                .addInterceptors(voiceStreamHandshakeInterceptor)
                .setHandshakeHandler(voiceStreamHandshakeHandler)
                .setAllowedOriginPatterns(VoiceStreamWebSocketPolicy.FRONTEND_ORIGIN_PATTERNS
                        .toArray(String[]::new));
    }
}
