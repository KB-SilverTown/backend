package com.silvertown.domain.voice.service.impl;

import com.silvertown.domain.transfer.dto.TransferConfirmRequest;
import com.silvertown.domain.transfer.dto.TransferConfirmResponse;
import com.silvertown.domain.transfer.service.TransferService;
import com.silvertown.domain.voice.service.TransferConfirmPort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransferServiceConfirmAdapter implements TransferConfirmPort {
    private final TransferService transferService;

    @Override
    public TransferConfirmResponse confirm(UUID userId, UUID transferId, boolean approved) {
        return transferService.confirm(userId, transferId, TransferConfirmRequest.of(approved));
    }
}
