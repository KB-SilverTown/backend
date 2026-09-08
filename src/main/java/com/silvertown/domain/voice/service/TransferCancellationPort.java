package com.silvertown.domain.voice.service;

import java.util.UUID;

/** Voice-domain boundary for cancelling an unexecuted transfer through the transfer domain. */
public interface TransferCancellationPort {
    void cancel(UUID userId, UUID transferId);
}
