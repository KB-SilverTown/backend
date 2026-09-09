package com.silvertown.domain.voice.websocket;

import java.security.Principal;
import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.RequestUpgradeStrategy;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.stereotype.Component;

@Component
public class VoiceStreamHandshakeHandler extends DefaultHandshakeHandler {
    public VoiceStreamHandshakeHandler() {
        setSupportedProtocols("voice-stream-v1");
    }

    VoiceStreamHandshakeHandler(RequestUpgradeStrategy requestUpgradeStrategy) {
        super(requestUpgradeStrategy);
        setSupportedProtocols("voice-stream-v1");
    }

    @Override
    protected Principal determineUser(
            ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) {
        Object principal = attributes.get(VoiceStreamPrincipal.HANDSHAKE_ATTRIBUTE);
        return principal instanceof Principal ? (Principal) principal : null;
    }
}
