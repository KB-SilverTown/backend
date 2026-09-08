package com.silvertown.domain.voice.service;

import com.silvertown.domain.transfer.dto.TransferPrepareRequest;
import com.silvertown.domain.transfer.dto.TransferPrepareResponse;
import java.util.UUID;

/** Voice-domain boundary for creating or reusing a transfer draft. */
public interface TransferPreparePort {
    TransferPrepareResponse prepare(UUID userId, TransferPrepareRequest request);
}
