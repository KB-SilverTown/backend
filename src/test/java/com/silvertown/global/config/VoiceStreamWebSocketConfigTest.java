package com.silvertown.global.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.silvertown.domain.voice.websocket.VoiceStreamWebSocketHandler;
import com.silvertown.domain.voice.websocket.VoiceStreamHandshakeHandler;
import com.silvertown.domain.voice.websocket.VoiceStreamHandshakeInterceptor;
import com.silvertown.domain.voice.websocket.VoiceStreamWebSocketPolicy;
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
import org.springframework.web.socket.server.HandshakeHandler;
import org.springframework.web.socket.server.support.OriginHandshakeInterceptor;

class VoiceStreamWebSocketConfigTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";
    private static final String ANDROID_WEBVIEW_ORIGIN = "https://localhost";

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

    @Test
    void allowsTheAndroidCapacitorWebViewOriginDuringTheWebSocketHandshake() throws Exception {
        OriginHandshakeInterceptor interceptor = registeredOriginInterceptor();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.beforeHandshake(
                requestFrom(ANDROID_WEBVIEW_ORIGIN),
                new ServletServerHttpResponse(response),
                mock(WebSocketHandler.class),
                new HashMap<>()));
    }

    private OriginHandshakeInterceptor registeredOriginInterceptor() throws Exception {
        VoiceStreamWebSocketHandler handler = mock(VoiceStreamWebSocketHandler.class);
        VoiceStreamHandshakeInterceptor ticketInterceptor = mock(VoiceStreamHandshakeInterceptor.class);
        VoiceStreamHandshakeHandler handshakeHandler = mock(VoiceStreamHandshakeHandler.class);
        VoiceStreamWebSocketPolicy policy =
                new VoiceStreamWebSocketPolicy(ALLOWED_ORIGIN + "," + ANDROID_WEBVIEW_ORIGIN);
        WebSocketHandlerRegistry registry = mock(WebSocketHandlerRegistry.class);
        WebSocketHandlerRegistration registration = mock(WebSocketHandlerRegistration.class);
        when(registry.addHandler(handler, "/api/voice/sessions/*/stream")).thenReturn(registration);
        when(registration.addInterceptors(ticketInterceptor)).thenReturn(registration);
        when(registration.setHandshakeHandler(org.mockito.ArgumentMatchers.any(HandshakeHandler.class)))
                .thenReturn(registration);

        VoiceStreamWebSocketConfig config = new VoiceStreamWebSocketConfig(
                handler, ticketInterceptor, handshakeHandler, policy);
        config.registerWebSocketHandlers(registry);

        ArgumentCaptor<String[]> originCaptor = ArgumentCaptor.forClass(String[].class);
        verify(registry).addHandler(handler, "/api/voice/sessions/*/stream");
        verify(registration).addInterceptors(ticketInterceptor);
        verify(registration).setHandshakeHandler(handshakeHandler);
        verify(registration).setAllowedOrigins(originCaptor.capture());
        assertNotNull(originCaptor.getValue());

        OriginHandshakeInterceptor interceptor = new OriginHandshakeInterceptor(List.of());
        interceptor.setAllowedOrigins(List.of(originCaptor.getValue()));
        return interceptor;
    }

    private ServletServerHttpRequest requestFrom(String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/voice/sessions/session-1/stream");
        request.addHeader(HttpHeaders.ORIGIN, origin);
        return new ServletServerHttpRequest(request);
    }
}
