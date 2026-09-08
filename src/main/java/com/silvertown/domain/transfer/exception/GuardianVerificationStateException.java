package com.silvertown.domain.transfer.exception;

import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;

/**
 * Signals a guardian-verification state transition that must be committed before the API error is
 * returned. For example, an invalid code must consume an attempt even though the request fails.
 */
public class GuardianVerificationStateException extends BusinessException {

    public GuardianVerificationStateException(ErrorCode errorCode) {
        super(errorCode);
    }
}
