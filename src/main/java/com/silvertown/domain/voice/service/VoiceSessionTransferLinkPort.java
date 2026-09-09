package com.silvertown.domain.voice.service;

import java.util.UUID;

/** Links a transfer created by the transfer domain to its owning voice session. */
public interface VoiceSessionTransferLinkPort {
    void link(UUID userId, UUID voiceSessionId, UUID transferId);
}
