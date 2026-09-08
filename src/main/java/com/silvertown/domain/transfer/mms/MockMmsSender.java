package com.silvertown.domain.transfer.mms;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 데모용 MMS 전송기. 실제 연락처나 인증번호를 로그·HTTP 응답에 노출하지 않는다. */
@Component
public class MockMmsSender implements MmsSender {
    private final boolean demoInboxEnabled;
    private final ConcurrentMap<UUID, DemoMessage> sentMessages = new ConcurrentHashMap<>();

    @Autowired
    public MockMmsSender(@Value("${demo.guardian-inbox.enabled:false}") boolean demoInboxEnabled) {
        this.demoInboxEnabled = demoInboxEnabled;
    }

    public MmsSendResult send(UUID verificationId, String code, OffsetDateTime sentAt) {
        if (!demoInboxEnabled) {
            return MmsSendResult.failed(MmsSendResult.FailureCode.MMS_PERMANENT_FAILURE);
        }
        sentMessages.put(verificationId, new DemoMessage(code, sentAt));
        return MmsSendResult.success();
    }

    public boolean wasSent(UUID verificationId) {
        return sentMessages.containsKey(verificationId);
    }

    public String receivedCode(UUID verificationId) {
        DemoMessage message = sentMessages.get(verificationId);
        return message == null ? null : message.code();
    }

    public boolean isDemoInboxEnabled() {
        return demoInboxEnabled;
    }

    /** A disabled demo inbox means this application has no configured MMS delivery channel. */
    public boolean isAvailable() {
        return demoInboxEnabled;
    }

    private record DemoMessage(String code, OffsetDateTime sentAt) {}
}
