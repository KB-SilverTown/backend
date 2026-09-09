package com.silvertown.domain.voice.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.RequestUpgradeStrategy;

class VoiceStreamHandshakeHandlerTest {
    @Test
    void usesTicketPrincipalAndOnlyNegotiatesTheVoiceStreamProtocol() {
        TestHandshakeHandler handler = new TestHandshakeHandler(Mockito.mock(RequestUpgradeStrategy.class));
        Map<String, Object> attributes = new HashMap<>();
        VoiceStreamPrincipal principal = new VoiceStreamPrincipal(
                "00000000-0000-0000-0000-000000000001");
        attributes.put(VoiceStreamPrincipal.HANDSHAKE_ATTRIBUTE, principal);

        assertEquals("voice-stream-v1", handler.getSupportedProtocols()[0]);
        assertEquals(principal, handler.userFor(attributes));
        assertNull(handler.userFor(new HashMap<>()));
    }

    private static class TestHandshakeHandler extends VoiceStreamHandshakeHandler {
        private TestHandshakeHandler(RequestUpgradeStrategy requestUpgradeStrategy) {
            super(requestUpgradeStrategy);
        }

        private Principal userFor(Map<String, Object> attributes) {
            return determineUser(
                    Mockito.mock(ServerHttpRequest.class), Mockito.mock(WebSocketHandler.class), attributes);
        }
    }
}
