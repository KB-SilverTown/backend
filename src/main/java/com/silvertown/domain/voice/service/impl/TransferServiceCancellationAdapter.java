package com.silvertown.domain.voice.service.impl;

import com.silvertown.domain.transfer.service.TransferService;
import com.silvertown.domain.voice.service.TransferCancellationPort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransferServiceCancellationAdapter implements TransferCancellationPort {
    private final TransferService transferService;

    @Override
    public void cancel(UUID userId, UUID transferId) {
        transferService.cancelUnexecuted(userId, transferId);
    }
}
