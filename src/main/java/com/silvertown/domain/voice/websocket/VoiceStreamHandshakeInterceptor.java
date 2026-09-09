package com.silvertown.domain.voice.websocket;

import com.silvertown.domain.voice.service.VoiceStreamTicketService;
import com.silvertown.domain.voice.validation.VoiceIdentifierPattern;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.OriginHandshakeInterceptor;

@Component
public class VoiceStreamHandshakeInterceptor implements HandshakeInterceptor {
    private static final String STREAM_PROTOCOL = "voice-stream-v1";
    private static final String TICKET_PROTOCOL_PREFIX = "ticket.";
    private static final String WEBSOCKET_PROTOCOL_HEADER = "Sec-WebSocket-Protocol";

    private final VoiceStreamTicketService voiceStreamTicketService;
    private final OriginHandshakeInterceptor originHandshakeInterceptor;

    public VoiceStreamHandshakeInterceptor(
            VoiceStreamTicketService voiceStreamTicketService,
            VoiceStreamWebSocketPolicy voiceStreamWebSocketPolicy) {
        this.voiceStreamTicketService = voiceStreamTicketService;
        this.originHandshakeInterceptor = new OriginHandshakeInterceptor(List.of());
        this.originHandshakeInterceptor.setAllowedOrigins(voiceStreamWebSocketPolicy.allowedOrigins());
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) throws Exception {
        if (!originHandshakeInterceptor.beforeHandshake(request, response, wsHandler, attributes)) {
            return false;
        }
        String sessionId = sessionIdFrom(request.getURI());
        String opaqueTicket = ticketFrom(request.getHeaders().get(WEBSOCKET_PROTOCOL_HEADER));
        if (sessionId == null || opaqueTicket == null) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }

        return voiceStreamTicketService.consumeForHandshake(sessionId, opaqueTicket)
                .map(userId -> {
                    attributes.put(
                            VoiceStreamPrincipal.HANDSHAKE_ATTRIBUTE,
                            new VoiceStreamPrincipal(userId));
                    return true;
                })
                .orElseGet(() -> {
                    response.setStatusCode(HttpStatus.FORBIDDEN);
                    return false;
                });
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {}

    private String sessionIdFrom(URI requestUri) {
        if (requestUri == null) {
            return null;
        }
        String[] pathParts = requestUri.getPath().split("/");
        if (pathParts.length != 6
                || !"api".equals(pathParts[1])
                || !"voice".equals(pathParts[2])
                || !"sessions".equals(pathParts[3])
                || !"stream".equals(pathParts[5])
                || !pathParts[4].matches(VoiceIdentifierPattern.CANONICAL_UUID_REGEX)) {
            return null;
        }
        return pathParts[4];
    }

    private String ticketFrom(List<String> requestedProtocols) {
        if (requestedProtocols == null) {
            return null;
        }
        List<String> protocols = requestedProtocols.stream()
                .flatMap(headerValue -> Arrays.stream(headerValue.split(",")))
                .map(String::trim)
                .toList();
        if (!protocols.contains(STREAM_PROTOCOL)) {
            return null;
        }
        List<String> ticketProtocols = protocols.stream()
                .filter(protocol -> protocol.startsWith(TICKET_PROTOCOL_PREFIX))
                .toList();
        if (ticketProtocols.size() != 1) {
            return null;
        }
        String opaqueTicket = ticketProtocols.get(0).substring(TICKET_PROTOCOL_PREFIX.length());
        return opaqueTicket.isBlank() ? null : opaqueTicket;
    }
}
