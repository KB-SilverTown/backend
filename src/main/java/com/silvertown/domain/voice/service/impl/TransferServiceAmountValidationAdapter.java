package com.silvertown.domain.voice.service.impl;

import com.silvertown.domain.transfer.dto.AmountValidationRequest;
import com.silvertown.domain.transfer.dto.AmountValidationResponse;
import com.silvertown.domain.transfer.service.TransferService;
import com.silvertown.domain.voice.service.AmountValidationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransferServiceAmountValidationAdapter implements AmountValidationPort {
    private final TransferService transferService;

    @Override
    public AmountValidationResponse validate(AmountValidationRequest request) {
        return transferService.validateAmount(request);
    }
}
