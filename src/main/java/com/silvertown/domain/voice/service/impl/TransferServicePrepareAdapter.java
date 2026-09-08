package com.silvertown.domain.voice.service.impl;

import com.silvertown.domain.transfer.dto.TransferPrepareRequest;
import com.silvertown.domain.transfer.dto.TransferPrepareResponse;
import com.silvertown.domain.transfer.service.TransferService;
import com.silvertown.domain.voice.service.TransferPreparePort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransferServicePrepareAdapter implements TransferPreparePort {
    private final TransferService transferService;

    @Override
    public TransferPrepareResponse prepare(UUID userId, TransferPrepareRequest request) {
        return transferService.prepare(userId, request);
    }
}
