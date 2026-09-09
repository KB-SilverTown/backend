package com.silvertown.domain.transfer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.isNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvertown.domain.transfer.dto.TransferPinRequest;
import com.silvertown.domain.transfer.dto.TransferResultResponse;
import com.silvertown.domain.transfer.mapper.TransferMapper;
import com.silvertown.domain.transfer.service.impl.TransferServiceImpl;
import com.silvertown.domain.transfer.vo.Transfer;
import com.silvertown.domain.transfer.vo.TransferAuthentication;
import com.silvertown.domain.transfer.vo.TransferTransaction;
import com.silvertown.domain.transfer.vo.UserTransferPin;
import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import com.silvertown.domain.transfer.mms.MockMmsSender;
import com.silvertown.global.security.SensitiveDataHasher;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class TransferAuthenticationServiceTest {
    private static final String CONFIRMATION_TOKEN = "confirmation-token";
    private final TransferMapper mapper = Mockito.mock(TransferMapper.class);
    private final SensitiveDataHasher sensitiveDataHasher =
            new SensitiveDataHasher("test-guardian-hmac-secret");
    private final TransferService service = new TransferServiceImpl(null, null, mapper, null, null,
            new ObjectMapper(), Clock.fixed(Instant.parse("2026-09-03T00:00:00Z"), ZoneOffset.UTC),
            sensitiveDataHasher, new MockMmsSender(false));

    @Test
    void authenticatesWithRegisteredPin() throws Exception {
        UUID userId = UUID.randomUUID(); UUID transferId = UUID.randomUUID();
        when(mapper.findOwnedByIdForUpdate(anyString(), anyString())).thenReturn(confirmed(transferId, userId));
        UserTransferPin pin = new UserTransferPin(); pin.setPinHash(new BCryptPasswordEncoder().encode("123456"));
        when(mapper.findPinForUpdate(userId.toString())).thenReturn(pin);

        var response = service.authenticate(userId, transferId, CONFIRMATION_TOKEN, pinRequest());

        assertEquals(true, response.isAuthenticated());
        assertEquals(OffsetDateTime.parse("2026-09-03T00:05:00Z"), response.getExpiresAt());
    }

    @Test
    void resetsExpiredLockBeforeRecordingNewFailure() throws Exception {
        UUID userId = UUID.randomUUID(); UUID transferId = UUID.randomUUID();
        when(mapper.findOwnedByIdForUpdate(anyString(), anyString())).thenReturn(confirmed(transferId, userId));
        UserTransferPin pin = new UserTransferPin(); pin.setFailedAttemptCount(5);
        pin.setLockedUntil(OffsetDateTime.parse("2026-09-02T23:59:59Z"));
        pin.setPinHash(new BCryptPasswordEncoder().encode("123456"));
        when(mapper.findPinForUpdate(userId.toString())).thenReturn(pin);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.authenticate(userId, transferId, CONFIRMATION_TOKEN, pinRequest("999999")));

        assertEquals(ErrorCode.TRANSFER_PIN_INVALID, exception.getErrorCode());
        verify(mapper).resetPinFailures(userId.toString());
        verify(mapper).recordPinFailure(eq(userId.toString()), isNull());
    }
    @Test
    void returnsExistingResultForSameIdempotencyKey() {
        UUID userId = UUID.randomUUID(); UUID transferId = UUID.randomUUID();
        TransferTransaction transaction = transaction(transferId);
        when(mapper.findTransactionByIdempotencyKey(userId.toString(), "key")).thenReturn(transaction);

        assertEquals(transaction.getTransactionId(), service.execute(
                userId, transferId, CONFIRMATION_TOKEN, "key").getTransactionId().toString());
    }

    @Test
    void returnsExistingResultAfterAcquiringTransferLock() {
        UUID userId = UUID.randomUUID(); UUID transferId = UUID.randomUUID();
        TransferTransaction transaction = transaction(transferId);
        when(mapper.findTransactionByIdempotencyKey(userId.toString(), "same-key")).thenReturn(null, transaction);
        when(mapper.findOwnedByIdForUpdate(userId.toString(), transferId.toString())).thenReturn(confirmed(transferId, userId));

        assertEquals(transaction.getTransactionId(), service.execute(
                userId, transferId, CONFIRMATION_TOKEN, "same-key").getTransactionId().toString());
        verify(mapper, times(2)).findTransactionByIdempotencyKey(userId.toString(), "same-key");
    }

    @Test
    void convertsConcurrentDuplicateKeyToExistingIdempotentResult() {
        UUID userId = UUID.randomUUID(); UUID transferId = UUID.randomUUID();
        TransferTransaction transaction = transaction(transferId);
        when(mapper.findTransactionByIdempotencyKey(userId.toString(), "same-key")).thenReturn(null, null, transaction);
        when(mapper.findOwnedByIdForUpdate(userId.toString(), transferId.toString())).thenReturn(confirmed(transferId, userId));
        TransferAuthentication authentication = activeAuthentication();
        when(mapper.findLatestAuthenticationForUpdate(anyString(), anyString())).thenReturn(authentication);
        doThrow(new DuplicateKeyException("duplicate")).when(mapper).insertTransaction(any());

        assertEquals(transaction.getTransactionId(), service.execute(
                userId, transferId, CONFIRMATION_TOKEN, "same-key").getTransactionId().toString());
    }

    @Test
    void executesAndConsumesTheAuthenticatedRecord() {
        UUID userId = UUID.randomUUID(); UUID transferId = UUID.randomUUID();
        TransferAuthentication authentication = activeAuthentication();
        when(mapper.findOwnedByIdForUpdate(userId.toString(), transferId.toString()))
                .thenReturn(confirmed(transferId, userId));
        when(mapper.findLatestAuthenticationForUpdate(userId.toString(), transferId.toString()))
                .thenReturn(authentication);
        when(mapper.executeIfConfirmed(userId.toString(), transferId.toString())).thenReturn(1);
        when(mapper.consumeAuthentication(authentication.getTransferAuthenticationId())).thenReturn(1);

        TransferResultResponse response = service.execute(
                userId, transferId, CONFIRMATION_TOKEN, "execution-key");

        assertEquals(transferId, response.getTransferId());
        verify(mapper).executeIfConfirmed(userId.toString(), transferId.toString());
        verify(mapper).consumeAuthentication(authentication.getTransferAuthenticationId());
    }

    @Test
    void rejectsSameUserKeyForAnotherTransfer() {
        UUID userId = UUID.randomUUID(); UUID transferId = UUID.randomUUID();
        when(mapper.findTransactionByIdempotencyKey(userId.toString(), "key")).thenReturn(transaction(UUID.randomUUID()));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.execute(userId, transferId, CONFIRMATION_TOKEN, "key"));
        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT, exception.getErrorCode());
    }

    @Test
    void rejectsExpiredAuthenticationBeforeExecuting() {
        UUID userId = UUID.randomUUID(); UUID transferId = UUID.randomUUID();
        when(mapper.findTransactionByIdempotencyKey(anyString(), anyString())).thenReturn(null);
        when(mapper.findOwnedByIdForUpdate(anyString(), anyString())).thenReturn(confirmed(transferId, userId));
        TransferAuthentication authentication = activeAuthentication();
        authentication.setExpiresAt(OffsetDateTime.parse("2026-09-02T23:59:59Z"));
        when(mapper.findLatestAuthenticationForUpdate(anyString(), anyString())).thenReturn(authentication);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.execute(userId, transferId, CONFIRMATION_TOKEN, "key"));
        assertEquals(ErrorCode.TRANSFER_AUTHENTICATION_EXPIRED, exception.getErrorCode());
    }

    @Test
    void rejectsPinAuthenticationWithoutTheConfirmationToken() throws Exception {
        UUID userId = UUID.randomUUID(); UUID transferId = UUID.randomUUID();
        when(mapper.findOwnedByIdForUpdate(anyString(), anyString())).thenReturn(confirmed(transferId, userId));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.authenticate(userId, transferId, null, pinRequest()));

        assertEquals(ErrorCode.TRANSFER_CONFIRMATION_REQUIRED, exception.getErrorCode());
    }

    @Test
    void rejectsPinAuthenticationForMismatchedConfirmationToken() throws Exception {
        UUID userId = UUID.randomUUID(); UUID transferId = UUID.randomUUID();
        when(mapper.findOwnedByIdForUpdate(anyString(), anyString())).thenReturn(confirmed(transferId, userId));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.authenticate(userId, transferId, "different-token", pinRequest()));

        assertEquals(ErrorCode.TRANSFER_CONFIRMATION_INVALID, exception.getErrorCode());
    }

    @Test
    void rejectsExecutionAfterTheConfirmationTokenExpires() {
        UUID userId = UUID.randomUUID(); UUID transferId = UUID.randomUUID();
        Transfer transfer = confirmed(transferId, userId);
        transfer.setConfirmationTokenExpiresAt(OffsetDateTime.parse("2026-09-02T23:59:59Z"));
        when(mapper.findTransactionByIdempotencyKey(anyString(), anyString())).thenReturn(null);
        when(mapper.findOwnedByIdForUpdate(anyString(), anyString())).thenReturn(transfer);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.execute(userId, transferId, CONFIRMATION_TOKEN, "key"));

        assertEquals(ErrorCode.TRANSFER_CONFIRMATION_EXPIRED, exception.getErrorCode());
    }

   private TransferAuthentication activeAuthentication() {
    TransferAuthentication authentication = new TransferAuthentication();
    authentication.setStatus("AUTHENTICATED");
    authentication.setTransferAuthenticationId(UUID.randomUUID().toString());
    authentication.setExpiresAt(
            OffsetDateTime.parse("2026-09-03T00:05:00Z"));
    return authentication;
}
    private TransferTransaction transaction(UUID transferId) {
        TransferTransaction transaction = new TransferTransaction();
        transaction.setTransactionId(UUID.randomUUID().toString()); transaction.setTransferId(transferId.toString());
        transaction.setStatus("SUCCESS"); transaction.setAmount(50_000L); return transaction;
    }
    private TransferPinRequest pinRequest() throws Exception { return pinRequest("123456"); }
    private TransferPinRequest pinRequest(String pin) throws Exception { return new ObjectMapper().readValue("{\"pin\":\"" + pin + "\"}", TransferPinRequest.class); }
    private Transfer confirmed(UUID transferId, UUID userId) {
        Transfer transfer = new Transfer(); transfer.setTransferId(transferId.toString()); transfer.setUserId(userId.toString());
        transfer.setStatus("CONFIRMED"); transfer.setFromAccountId(UUID.randomUUID().toString()); transfer.setRecipientId(UUID.randomUUID().toString()); transfer.setAmount(50_000L);
        transfer.setConfirmationTokenHash(sensitiveDataHasher.hash(CONFIRMATION_TOKEN));
        transfer.setConfirmationTokenExpiresAt(OffsetDateTime.parse("2026-09-03T00:05:00Z"));
        return transfer;
    }
}
