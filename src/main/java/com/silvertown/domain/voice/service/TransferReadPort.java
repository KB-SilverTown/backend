package com.silvertown.domain.voice.service;

import com.silvertown.domain.transfer.dto.TransferResponse;
import java.util.UUID;

/** Reads the canonical transfer values that are safe to repeat back to the user. */
public interface TransferReadPort {
    TransferResponse get(UUID userId, UUID transferId);
}
