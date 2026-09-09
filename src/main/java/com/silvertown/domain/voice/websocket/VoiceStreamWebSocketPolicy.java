package com.silvertown.domain.voice.websocket;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class VoiceStreamWebSocketPolicy {
    private final List<String> allowedOrigins;

    public VoiceStreamWebSocketPolicy(
            @Value("${voice.stream.allowed-origins:${VOICE_STREAM_ALLOWED_ORIGINS:http://localhost:5173,http://localhost,https://localhost}}")
                    String configuredOrigins) {
        this.allowedOrigins = parseAllowedOrigins(configuredOrigins);
    }

    public List<String> allowedOrigins() {
        return allowedOrigins;
    }

    private List<String> parseAllowedOrigins(String configuredOrigins) {
        if (configuredOrigins == null || configuredOrigins.isBlank()) {
            throw new IllegalArgumentException("voice.stream.allowed-origins must not be blank.");
        }

        List<String> origins = Arrays.stream(configuredOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        if (origins.isEmpty() || origins.stream().anyMatch(origin -> !isExplicitOrigin(origin))) {
            throw new IllegalArgumentException(
                    "voice.stream.allowed-origins must contain only explicit http, https, or capacitor origins.");
        }
        return origins;
    }

    private boolean isExplicitOrigin(String origin) {
        if (origin.contains("*")) {
            return false;
        }
        try {
            URI uri = URI.create(origin);
            return uri.getScheme() != null
                    && uri.getHost() != null
                    && ("http".equals(uri.getScheme())
                            || "https".equals(uri.getScheme())
                            || "capacitor".equals(uri.getScheme()))
                    && uri.getPath().isEmpty()
                    && uri.getQuery() == null
                    && uri.getFragment() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
