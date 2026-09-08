package com.silvertown.domain.transfer.mms;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 보호자 인증번호 발송 채널의 동기식 결과를 제공한다. */
public interface MmsSender {
    boolean isAvailable();

    MmsSendResult send(UUID verificationId, String code, OffsetDateTime sentAt);
}
