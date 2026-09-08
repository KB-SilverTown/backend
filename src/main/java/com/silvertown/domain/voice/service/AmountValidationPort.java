package com.silvertown.domain.voice.service;

import com.silvertown.domain.transfer.dto.AmountValidationRequest;
import com.silvertown.domain.transfer.dto.AmountValidationResponse;

/** Voice-domain boundary for already-normalized transfer amount candidates. */
public interface AmountValidationPort {
    AmountValidationResponse validate(AmountValidationRequest request);
}
