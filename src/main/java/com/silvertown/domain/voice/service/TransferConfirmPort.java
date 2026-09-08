package com.silvertown.domain.voice.service;

import com.silvertown.domain.transfer.dto.TransferConfirmResponse;
import java.util.UUID;

/** Voice-domain boundary for an explicit final approval or cancellation. */
public interface TransferConfirmPort {
    TransferConfirmResponse confirm(UUID userId, UUID transferId, boolean approved);
}
