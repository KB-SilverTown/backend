package com.silvertown.domain.transfer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvertown.domain.transfer.mapper.TransferMapper;
import com.silvertown.domain.transfer.mms.MmsSendResult;
import com.silvertown.domain.transfer.mms.MmsSender;
import com.silvertown.domain.transfer.service.impl.TransferServiceImpl;
import com.silvertown.domain.transfer.vo.GuardianVerification;
import com.silvertown.domain.transfer.vo.Transfer;
import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import com.silvertown.global.security.SensitiveDataHasher;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class GuardianMmsDeliveryPolicyTest {
    private final TransferMapper mapper = Mockito.mock(TransferMapper.class);
    private final UUID userId = UUID.randomUUID();
    private final UUID transferId = UUID.randomUUID();

    @Test
    void retriesTemporaryFailureOnceWithoutRecordingAnExtraDeliveryAttempt() {
        MmsSender sender = Mockito.mock(MmsSender.class);
        when(sender.isAvailable()).thenReturn(true);
        when(sender.send(any(), anyString(), any())).thenReturn(
                MmsSendResult.failed(MmsSendResult.FailureCode.MMS_TEMPORARY_FAILURE),
                MmsSendResult.success());
        prepareHeldTransfer();

        var response = service(sender).startGuardianVerification(userId, transferId, null);

        assertEquals("SENT", response.getStatus());
        assertEquals(null, response.getDeliveryFailureCode());
        verify(sender, times(2)).send(any(), anyString(), any());
        verify(mapper).insertGuardianVerification(any(GuardianVerification.class));
    }

    @Test
    void recordsProviderTimeoutAsFailedDeliveryWithoutAutomaticRetry() {
        MmsSender sender = Mockito.mock(MmsSender.class);
        when(sender.isAvailable()).thenReturn(true);
        when(sender.send(any(), anyString(), any())).thenReturn(
                MmsSendResult.failed(MmsSendResult.FailureCode.MMS_PROVIDER_TIMEOUT));
        prepareHeldTransfer();
        ArgumentCaptor<GuardianVerification> verification = ArgumentCaptor.forClass(GuardianVerification.class);

        var response = service(sender).startGuardianVerification(userId, transferId, null);

        assertEquals("FAILED", response.getStatus());
        assertEquals("MMS_PROVIDER_TIMEOUT", response.getDeliveryFailureCode());
        verify(sender).send(any(), anyString(), any());
        verify(mapper).insertGuardianVerification(verification.capture());
        assertEquals("FAILED", verification.getValue().getStatus());
    }

    @Test
    void rejectsFourthRecordedDeliveryAttempt() {
        MmsSender sender = Mockito.mock(MmsSender.class);
        when(sender.isAvailable()).thenReturn(true);
        prepareHeldTransfer();
        when(mapper.countGuardianDeliveryAttempts(transferId.toString())).thenReturn(3);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service(sender).startGuardianVerification(userId, transferId, null));

        assertEquals(ErrorCode.GUARDIAN_DELIVERY_ATTEMPT_EXCEEDED, exception.getErrorCode());
        verify(sender, Mockito.never()).send(any(), anyString(), any());
    }

    @Test
    void rejectsResendDuringSixtySecondCooldown() {
        MmsSender sender = Mockito.mock(MmsSender.class);
        when(sender.isAvailable()).thenReturn(true);
        prepareHeldTransfer();
        GuardianVerification latest = new GuardianVerification();
        latest.setSentAt(OffsetDateTime.parse("2026-09-06T00:00:30Z"));
        when(mapper.findLatestGuardianDeliveryForUpdate(transferId.toString())).thenReturn(latest);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service(sender).startGuardianVerification(userId, transferId, null));

        assertEquals(ErrorCode.GUARDIAN_RESEND_NOT_AVAILABLE, exception.getErrorCode());
        verify(sender, Mockito.never()).send(any(), anyString(), any());
    }

    @Test
    void expiresExistingCodeBeforeSendingResendAfterCooldown() {
        MmsSender sender = Mockito.mock(MmsSender.class);
        when(sender.isAvailable()).thenReturn(true);
        when(sender.send(any(), anyString(), any())).thenReturn(MmsSendResult.success());
        prepareHeldTransfer();
        GuardianVerification active = new GuardianVerification();
        active.setVerificationId(UUID.randomUUID().toString());
        active.setSentAt(OffsetDateTime.parse("2026-09-05T23:58:59Z"));
        when(mapper.findLatestGuardianDeliveryForUpdate(transferId.toString())).thenReturn(active);
        when(mapper.findActiveGuardianVerificationForUpdate(transferId.toString())).thenReturn(active);

        service(sender).startGuardianVerification(userId, transferId, null);

        verify(mapper).expireGuardianVerification(active.getVerificationId());
        verify(sender).send(any(), anyString(), any());
    }

    private TransferService service(MmsSender sender) {
        return new TransferServiceImpl(null, null, mapper, null, null, new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-09-06T00:00:00Z"), ZoneOffset.UTC),
                new SensitiveDataHasher("test-guardian-hmac-secret"), sender);
    }

    private void prepareHeldTransfer() {
        Transfer transfer = new Transfer();
        transfer.setStatus("HELD");
        when(mapper.findOwnedByIdForUpdate(userId.toString(), transferId.toString())).thenReturn(transfer);
        when(mapper.findActiveGuardianVerificationForUpdate(transferId.toString())).thenReturn(null);
        when(mapper.findEmergencyContactPhoneHash(userId.toString())).thenReturn("phone-hash");
        when(mapper.countGuardianDeliveryAttempts(transferId.toString())).thenReturn(0);
    }
}
