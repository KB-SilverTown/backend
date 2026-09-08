package com.silvertown.global.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청값을 확인해 주세요."),
    AUTH_REQUEST_INVALID(HttpStatus.BAD_REQUEST, "AUTH_REQUEST_INVALID", "로그인 요청값을 확인해 주세요."),
    SIGNUP_REQUEST_INVALID(HttpStatus.BAD_REQUEST, "SIGNUP_REQUEST_INVALID", "회원가입 요청값을 확인해 주세요."),
    REQUIRED_CONSENT_MISSING(HttpStatus.BAD_REQUEST, "REQUIRED_CONSENT_MISSING", "필수 약관 동의가 필요합니다."),
    LOGIN_ID_DUPLICATE(HttpStatus.CONFLICT, "LOGIN_ID_DUPLICATE", "이미 사용 중인 아이디입니다."),
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS", "아이디 또는 비밀번호가 올바르지 않습니다."),
    AUTH_TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "AUTH_TOO_MANY_ATTEMPTS", "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    REFRESH_TOKEN_MISSING(HttpStatus.BAD_REQUEST, "REFRESH_TOKEN_MISSING", "리프레시 토큰이 필요합니다."),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID", "리프레시 토큰이 올바르지 않습니다."),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_EXPIRED", "리프레시 토큰이 만료되었습니다."),
    REFRESH_TOKEN_REUSED(HttpStatus.CONFLICT, "REFRESH_TOKEN_REUSED", "이미 사용되었거나 폐기된 리프레시 토큰입니다."),
    JWT_NOT_CONFIGURED(HttpStatus.INTERNAL_SERVER_ERROR, "JWT_NOT_CONFIGURED", "인증 토큰 설정이 필요합니다."),
    JWT_FAILURE(HttpStatus.INTERNAL_SERVER_ERROR, "JWT_FAILURE", "인증 토큰을 처리할 수 없습니다."),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "인증이 필요합니다."),
    INVALID_AUTHENTICATED_USER(HttpStatus.UNAUTHORIZED, "INVALID_AUTHENTICATED_USER", "인증 사용자 식별자가 올바르지 않습니다."),
    ACCOUNT_CRYPTO_NOT_CONFIGURED(HttpStatus.INTERNAL_SERVER_ERROR, "ACCOUNT_CRYPTO_NOT_CONFIGURED", "계좌정보 암호화 설정이 필요합니다."),
    ACCOUNT_CRYPTO_FAILURE(HttpStatus.INTERNAL_SERVER_ERROR, "ACCOUNT_CRYPTO_FAILURE", "계좌정보를 안전하게 처리할 수 없습니다."),
    INVALID_TRANSFER_AMOUNT(HttpStatus.BAD_REQUEST, "INVALID_TRANSFER_AMOUNT", "송금 금액은 원 단위 양수여야 합니다."),
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "출금 계좌를 찾을 수 없습니다."),
    RECIPIENT_NOT_FOUND(HttpStatus.NOT_FOUND, "RECIPIENT_NOT_FOUND", "수취인을 찾을 수 없습니다."),
    REMINDER_QUERY_INVALID(HttpStatus.BAD_REQUEST, "REMINDER_QUERY_INVALID", "리마인더 조회 조건을 확인해 주세요."),
    REMINDER_SCHEDULE_INVALID(HttpStatus.BAD_REQUEST, "REMINDER_SCHEDULE_INVALID", "리마인더 예약 시각을 확인해 주세요."),
    BILL_NOT_FOUND(HttpStatus.NOT_FOUND, "BILL_NOT_FOUND", "고지서를 찾을 수 없습니다."),
    BILL_IMAGE_INVALID(HttpStatus.BAD_REQUEST, "BILL_IMAGE_INVALID", "고지서 이미지를 확인해 주세요."),
    BILL_OCR_FAILED(HttpStatus.BAD_GATEWAY, "BILL_OCR_FAILED", "고지서 내용을 읽지 못했습니다. 다시 촬영해 주세요."),
    BILL_CONFIRMATION_INVALID(HttpStatus.BAD_REQUEST, "BILL_CONFIRMATION_INVALID", "고지서 확인값을 확인해 주세요."),
    BILL_INVALID_STATE(HttpStatus.CONFLICT, "BILL_INVALID_STATE", "현재 상태에서는 고지서를 처리할 수 없습니다."),
    REMINDER_DUPLICATE(HttpStatus.CONFLICT, "REMINDER_DUPLICATE", "같은 리마인더가 이미 등록되어 있습니다."),
    VOICE_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "VOICE_SESSION_NOT_FOUND", "음성 세션을 찾을 수 없습니다."),
    VOICE_TURN_CONFLICT(HttpStatus.CONFLICT, "VOICE_TURN_CONFLICT", "현재 음성 대화 턴을 처리할 수 없습니다."),
    VOICE_CARD_STALE(HttpStatus.CONFLICT, "VOICE_CARD_STALE", "화면 정보가 변경되었어요. 최신 화면을 다시 확인해 주세요."),
    VOICE_UI_ACTION_CONFLICT(HttpStatus.CONFLICT, "VOICE_UI_ACTION_CONFLICT", "같은 화면 요청의 내용이 서로 달라 처리할 수 없습니다."),
    SPEECH_NOT_CONFIGURED(HttpStatus.INTERNAL_SERVER_ERROR, "SPEECH_NOT_CONFIGURED", "Azure Speech 설정이 필요합니다."),
    SPEECH_TOKEN_ISSUANCE_FAILED(HttpStatus.BAD_GATEWAY, "SPEECH_TOKEN_ISSUANCE_FAILED", "Azure Speech 토큰을 발급할 수 없습니다."),
    SPEECH_RECOGNITION_FAILED(HttpStatus.BAD_GATEWAY, "SPEECH_RECOGNITION_FAILED", "음성을 인식하지 못했습니다. 다시 말씀해 주세요."),
    LLM_NOT_CONFIGURED(HttpStatus.INTERNAL_SERVER_ERROR, "LLM_NOT_CONFIGURED", "AI 분석 설정이 필요합니다."),
    LLM_ANALYSIS_FAILED(HttpStatus.BAD_GATEWAY, "LLM_ANALYSIS_FAILED", "AI 음성 분석을 완료할 수 없습니다."),
    TRANSFER_NOT_FOUND(HttpStatus.NOT_FOUND, "TRANSFER_NOT_FOUND", "송금 정보를 찾을 수 없습니다."),
    INSUFFICIENT_BALANCE(HttpStatus.CONFLICT, "INSUFFICIENT_BALANCE", "계좌 잔액이 부족합니다."),
    TRANSFER_INVALID_STATE(HttpStatus.CONFLICT, "TRANSFER_INVALID_STATE", "현재 상태에서는 송금을 처리할 수 없습니다."),
    RISK_CHECK_REQUIRED(HttpStatus.UNPROCESSABLE_ENTITY, "RISK_CHECK_REQUIRED", "위험도 확인을 먼저 완료해 주세요."),
    GUARDIAN_VERIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "GUARDIAN_VERIFICATION_NOT_FOUND", "보호자 인증 요청을 찾을 수 없습니다."),
    GUARDIAN_VERIFICATION_ALREADY_PENDING(HttpStatus.CONFLICT, "GUARDIAN_VERIFICATION_ALREADY_PENDING", "진행 중인 보호자 인증 요청이 있습니다."),
    GUARDIAN_DELIVERY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "GUARDIAN_DELIVERY_UNAVAILABLE", "보호자 인증번호를 전송할 수 없습니다. 잠시 후 다시 시도해 주세요."),
    GUARDIAN_DELIVERY_ATTEMPT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "GUARDIAN_DELIVERY_ATTEMPT_EXCEEDED", "보호자 인증번호 발송 횟수를 초과했습니다."),
    GUARDIAN_RESEND_NOT_AVAILABLE(HttpStatus.TOO_MANY_REQUESTS, "GUARDIAN_RESEND_NOT_AVAILABLE", "보호자 인증번호는 1분 후에 다시 보낼 수 있습니다."),
    GUARDIAN_CODE_INVALID(HttpStatus.BAD_REQUEST, "GUARDIAN_CODE_INVALID", "보호자 인증번호가 올바르지 않습니다."),
    GUARDIAN_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "GUARDIAN_CODE_EXPIRED", "보호자 인증번호가 만료되었습니다."),
    GUARDIAN_ATTEMPT_EXCEEDED(HttpStatus.CONFLICT, "GUARDIAN_ATTEMPT_EXCEEDED", "보호자 인증 시도 횟수를 초과했습니다."),
    TRANSFER_DATA_INVALID(HttpStatus.INTERNAL_SERVER_ERROR, "TRANSFER_DATA_INVALID", "저장된 송금 정보를 읽을 수 없습니다."),
    TRANSFER_PIN_NOT_REGISTERED(HttpStatus.NOT_FOUND, "TRANSFER_PIN_NOT_REGISTERED", "거래 승인 PIN을 먼저 등록해 주세요."),
    TRANSFER_PIN_INVALID(HttpStatus.UNAUTHORIZED, "TRANSFER_PIN_INVALID", "거래 승인 PIN이 올바르지 않습니다."),
    TRANSFER_PIN_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "TRANSFER_PIN_LOCKED", "거래 승인 PIN이 잠겼습니다. 잠시 후 다시 시도해 주세요."),
    TRANSFER_AUTHENTICATION_REQUIRED(HttpStatus.UNPROCESSABLE_ENTITY, "TRANSFER_AUTHENTICATION_REQUIRED", "송금 PIN 인증을 먼저 완료해 주세요."),
    TRANSFER_AUTHENTICATION_EXPIRED(HttpStatus.GONE, "TRANSFER_AUTHENTICATION_EXPIRED", "송금 PIN 인증 시간이 만료되었습니다. 다시 인증해 주세요."),
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key 헤더가 필요합니다."),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "동일한 요청 키가 다른 요청에 사용되었습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public boolean isRetryable() {
        return this == SPEECH_TOKEN_ISSUANCE_FAILED
                || this == SPEECH_RECOGNITION_FAILED
                || this == LLM_ANALYSIS_FAILED;
    }
}
