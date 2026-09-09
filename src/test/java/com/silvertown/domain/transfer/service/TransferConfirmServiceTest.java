package com.silvertown.domain.transfer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvertown.domain.transfer.dto.TransferConfirmRequest;
import com.silvertown.domain.transfer.dto.TransferConfirmResponse;
import com.silvertown.domain.transfer.mapper.TransferMapper;
import com.silvertown.domain.transfer.service.impl.TransferServiceImpl;
import com.silvertown.domain.transfer.vo.Transfer;
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

class TransferConfirmServiceTest {
    private final TransferMapper transferMapper = Mockito.mock(TransferMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SensitiveDataHasher sensitiveDataHasher =
            new SensitiveDataHasher("test-guardian-hmac-secret");
    private final TransferService service = new TransferServiceImpl(
            null, null, transferMapper, null, null, objectMapper,
            Clock.fixed(Instant.parse("2026-09-02T00:00:00Z"), ZoneOffset.UTC),
            sensitiveDataHasher, new MockMmsSender(false));

    @Test
    void confirmsRiskCheckedTransferAndWritesApprovalHistory() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        when(transferMapper.findOwnedByIdForUpdate(anyString(), anyString()))
                .thenReturn(transfer("RECONFIRM"));
        when(transferMapper.findLatestAdditionalCheckRequired(transferId.toString())).thenReturn(false);
        when(transferMapper.confirmIfRiskChecked(
                eq(userId.toString()), eq(transferId.toString()), anyString(), any())).thenReturn(1);

        TransferConfirmResponse response = service.confirm(userId, transferId, request(true));

        assertEquals("CONFIRMED", response.getStatus());
        assertEquals("AUTHENTICATE", response.getCurrentStep());
        assertEquals(true, response.isConfirmed());
        assertNotNull(response.getConfirmationToken());
        assertEquals(OffsetDateTime.parse("2026-09-02T00:05:00Z"),
                response.getConfirmationTokenExpiresAt());
        verify(transferMapper).confirmIfRiskChecked(
                eq(userId.toString()), eq(transferId.toString()),
                eq(sensitiveDataHasher.hash(response.getConfirmationToken())),
                eq(response.getConfirmationTokenExpiresAt()));
        verify(transferMapper).insertConfirmation(any());
    }

    @Test
    void rejectsConfirmationUntilAdditionalRiskCheckIsCompleted() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        when(transferMapper.findOwnedByIdForUpdate(anyString(), anyString()))
                .thenReturn(transfer("DRAFT"));
        when(transferMapper.findLatestAdditionalCheckRequired(transferId.toString())).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.confirm(userId, transferId, request(true)));

        assertEquals(ErrorCode.RISK_CHECK_REQUIRED, exception.getErrorCode());
        verify(transferMapper, never()).confirmIfRiskChecked(
                anyString(), anyString(), anyString(), any());
    }

    @Test
    void confirmsReconfirmationAfterGuardianVerificationEvenWhenRiskAssessmentRemainsHeld() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        when(transferMapper.findOwnedByIdForUpdate(anyString(), anyString()))
                .thenReturn(transfer("RECONFIRM"));
        when(transferMapper.findLatestAdditionalCheckRequired(transferId.toString())).thenReturn(true);
        when(transferMapper.confirmIfRiskChecked(
                eq(userId.toString()), eq(transferId.toString()), anyString(), any())).thenReturn(1);

        TransferConfirmResponse response = service.confirm(userId, transferId, request(true));

        assertEquals("CONFIRMED", response.getStatus());
        verify(transferMapper).confirmIfRiskChecked(
                eq(userId.toString()), eq(transferId.toString()), anyString(), any());
    }

    @Test
    void cancelsOnlyUnexecutedTransferWithAnAtomicConditionalUpdate() {
        UUID userId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();

        service.cancelUnexecuted(userId, transferId);

        verify(transferMapper).cancelUnexecutedIfPending(userId.toString(), transferId.toString());
        verify(transferMapper, never()).findOwnedByIdForUpdate(anyString(), anyString());
        verify(transferMapper, never()).cancelIfExecutable(anyString(), anyString());
    }

    private TransferConfirmRequest request(boolean approved) throws Exception {
        return objectMapper.readValue("{\"approved\":" + approved + "}", TransferConfirmRequest.class);
    }

    private Transfer transfer(String status) {
        Transfer transfer = new Transfer();
        transfer.setTransferId(UUID.randomUUID().toString());
        transfer.setUserId(UUID.randomUUID().toString());
        transfer.setStatus(status);
        transfer.setCurrentStep("RISK_CHECK");
        transfer.setRecipientDisplayName("홍길순");
        transfer.setAmount(100_000L);
        return transfer;
    }
}
