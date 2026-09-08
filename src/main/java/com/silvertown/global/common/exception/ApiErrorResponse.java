package com.silvertown.global.common.exception;

import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
public class ApiErrorResponse {

    private final String code;
    private final String message;
    private final String requestId;
    private final List<FieldErrorDetail> fieldErrors;
    private final boolean retryable;

    public ApiErrorResponse(
            String code,
            String message,
            String requestId,
            List<FieldErrorDetail> fieldErrors,
            boolean retryable
    ) {
        this.code = code;
        this.message = message;
        this.requestId = requestId;
        this.fieldErrors = fieldErrors;
        this.retryable = retryable;
    }

    public static ApiErrorResponse of(ErrorCode errorCode, String requestId) {
        return new ApiErrorResponse(
                errorCode.getCode(),
                errorCode.getMessage(),
                requestId,
                Collections.emptyList(),
                errorCode.isRetryable()
        );
    }

    public static ApiErrorResponse of(
            ErrorCode errorCode,
            String requestId,
            List<FieldErrorDetail> fieldErrors
    ) {
        return new ApiErrorResponse(
                errorCode.getCode(),
                errorCode.getMessage(),
                requestId,
                fieldErrors,
                errorCode.isRetryable()
        );
    }

    @Getter
    @AllArgsConstructor
    public static class FieldErrorDetail {

        private final String field;
        private final String reason;
    }
}
