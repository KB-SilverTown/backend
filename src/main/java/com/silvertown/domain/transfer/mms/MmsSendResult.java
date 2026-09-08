package com.silvertown.domain.transfer.mms;

/** 외부 MMS 사업자 결과를 송금 도메인에 노출하지 않는 최소 결과값이다. */
public record MmsSendResult(boolean delivered, FailureCode failureCode) {
    public enum FailureCode {
        MMS_TEMPORARY_FAILURE,
        MMS_PERMANENT_FAILURE,
        MMS_PROVIDER_TIMEOUT
    }

    public static MmsSendResult success() {
        return new MmsSendResult(true, null);
    }

    public static MmsSendResult failed(FailureCode failureCode) {
        return new MmsSendResult(false, failureCode);
    }
}
