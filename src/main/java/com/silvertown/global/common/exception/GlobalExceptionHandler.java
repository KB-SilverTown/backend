package com.silvertown.global.common.exception;

import com.silvertown.global.common.filter.RequestIdFilter;
import java.util.List;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.MultipartException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessException(
            BusinessException exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity
                .status(errorCode.getStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiErrorResponse.of(errorCode, getRequestId(request)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<ApiErrorResponse.FieldErrorDetail> fieldErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new ApiErrorResponse.FieldErrorDetail(error.getField(), error.getDefaultMessage()))
                .collect(Collectors.toList());

        return ResponseEntity
                .status(ErrorCode.INVALID_REQUEST.getStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiErrorResponse.of(ErrorCode.INVALID_REQUEST, getRequestId(request), fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(ErrorCode.INVALID_REQUEST.getStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiErrorResponse.of(ErrorCode.INVALID_REQUEST, getRequestId(request)));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(ErrorCode.INVALID_REQUEST.getStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiErrorResponse.of(ErrorCode.INVALID_REQUEST, getRequestId(request)));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(ErrorCode.INVALID_REQUEST.getStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiErrorResponse.of(ErrorCode.INVALID_REQUEST, getRequestId(request)));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiErrorResponse> handleMultipartException(
            MultipartException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(ErrorCode.BILL_IMAGE_INVALID.getStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiErrorResponse.of(ErrorCode.BILL_IMAGE_INVALID, getRequestId(request)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnhandledException(
            Exception exception,
            HttpServletRequest request
    ) {
        String requestId = getRequestId(request);
        log.error(
                "Unhandled exception. requestId={}, exceptionType={}",
                requestId,
                exception.getClass().getName()
        );

        return ResponseEntity
                .status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR, requestId));
    }

    private String getRequestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        return requestId != null ? requestId.toString() : MDC.get(RequestIdFilter.MDC_KEY);
    }
}
