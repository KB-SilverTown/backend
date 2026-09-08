package com.silvertown.global.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.silvertown.domain.voice.websocket.VoiceStreamWebSocketHandler;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.OriginHandshakeInterceptor;

class VoiceStreamWebSocketConfigTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";

    @Test
    void allowsTheConfirmedFrontendOriginDuringTheWebSocketHandshake() throws Exception {
        OriginHandshakeInterceptor interceptor = registeredOriginInterceptor();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.beforeHandshake(
                requestFrom(ALLOWED_ORIGIN),
                new ServletServerHttpResponse(response),
                mock(WebSocketHandler.class),
                new HashMap<>()));
    }

    @Test
    void rejectsAnUnconfiguredOriginDuringTheWebSocketHandshake() throws Exception {
        OriginHandshakeInterceptor interceptor = registeredOriginInterceptor();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.beforeHandshake(
                requestFrom("https://untrusted.example"),
                new ServletServerHttpResponse(response),
                mock(WebSocketHandler.class),
                new HashMap<>()));
        assertEquals(HttpStatus.FORBIDDEN.value(), response.getStatus());
    }

    private OriginHandshakeInterceptor registeredOriginInterceptor() throws Exception {
        VoiceStreamWebSocketHandler handler = mock(VoiceStreamWebSocketHandler.class);
        WebSocketHandlerRegistry registry = mock(WebSocketHandlerRegistry.class);
        WebSocketHandlerRegistration registration = mock(WebSocketHandlerRegistration.class);
        when(registry.addHandler(handler, "/api/voice/sessions/*/stream")).thenReturn(registration);

        VoiceStreamWebSocketConfig config = new VoiceStreamWebSocketConfig(handler);
        config.registerWebSocketHandlers(registry);

        ArgumentCaptor<String> originCaptor = ArgumentCaptor.forClass(String.class);
        verify(registry).addHandler(handler, "/api/voice/sessions/*/stream");
        verify(registration).setAllowedOrigins(originCaptor.capture());
        assertNotNull(originCaptor.getValue());

        return new OriginHandshakeInterceptor(List.of(originCaptor.getValue()));
    }

    private ServletServerHttpRequest requestFrom(String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/voice/sessions/session-1/stream");
        request.addHeader(HttpHeaders.ORIGIN, origin);
        return new ServletServerHttpRequest(request);
    }
}
