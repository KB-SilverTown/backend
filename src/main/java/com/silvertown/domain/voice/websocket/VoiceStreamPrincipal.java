package com.silvertown.domain.voice.websocket;

import java.security.Principal;

public class VoiceStreamPrincipal implements Principal {
    public static final String HANDSHAKE_ATTRIBUTE = VoiceStreamPrincipal.class.getName();

    private final String userId;

    public VoiceStreamPrincipal(String userId) {
        this.userId = userId;
    }

    @Override
    public String getName() {
        return userId;
    }
}
