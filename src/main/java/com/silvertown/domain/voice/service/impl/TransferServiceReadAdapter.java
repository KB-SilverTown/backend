package com.silvertown.domain.voice.service.impl;

import com.silvertown.domain.transfer.dto.TransferResponse;
import com.silvertown.domain.transfer.service.TransferService;
import com.silvertown.domain.voice.service.TransferReadPort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransferServiceReadAdapter implements TransferReadPort {
    private final TransferService transferService;

    @Override
    public TransferResponse get(UUID userId, UUID transferId) {
        return transferService.get(userId, transferId);
    }
}
