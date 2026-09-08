package com.silvertown.domain.bill.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.silvertown.domain.bill.client.BillOcrCandidate;
import com.silvertown.domain.bill.client.BillOcrClient;
import com.silvertown.domain.bill.dto.BillConfirmRequest;
import com.silvertown.domain.bill.dto.BillConfirmResponse;
import com.silvertown.domain.bill.dto.BillExecuteRequest;
import com.silvertown.domain.bill.dto.BillMonthlySummaryResponse;
import com.silvertown.domain.bill.dto.BillOcrResponse;
import com.silvertown.domain.bill.dto.BillPaymentResultResponse;
import com.silvertown.domain.bill.enums.BillStatus;
import com.silvertown.domain.bill.mapper.BillMapper;
import com.silvertown.domain.bill.service.impl.BillServiceImpl;
import com.silvertown.domain.bill.vo.BillVo;
import com.silvertown.domain.bill.vo.BillPaymentVo;
import com.silvertown.domain.bill.vo.BillMonthlyAggregateVo;
import com.silvertown.domain.voice.mapper.IdempotencyRecordMapper;
import com.silvertown.domain.voice.vo.IdempotencyRecordVo;
import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

class BillServiceImplTest {
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID VOICE_SESSION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID BILL_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-03T01:00:00Z"), ZoneId.of("Asia/Seoul"));

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private BillMapper billMapper;
    private BillOcrClient billOcrClient;
    private IdempotencyRecordMapper idempotencyRecordMapper;
    private BillService billService;

    @BeforeEach
    void setUp() {
        billMapper = Mockito.mock(BillMapper.class);
        billOcrClient = Mockito.mock(BillOcrClient.class);
        idempotencyRecordMapper = Mockito.mock(IdempotencyRecordMapper.class);
        billService = new BillServiceImpl(
                billMapper, billOcrClient, idempotencyRecordMapper, objectMapper, CLOCK, 300);
    }

    @Test
    void savesLowConfidenceOcrResultAsReconfirmDraft() {
        when(billMapper.existsOwnedVoiceSession(USER_ID.toString(), VOICE_SESSION_ID.toString()))
                .thenReturn(1);
        when(billOcrClient.analyze(any(), eq("image/jpeg"))).thenReturn(candidate("0.82"));

        BillOcrResponse response = billService.createOcrDraft(
                USER_ID, VOICE_SESSION_ID, new byte[] {1, 2, 3}, "image/jpeg");

        ArgumentCaptor<BillVo> billCaptor = ArgumentCaptor.forClass(BillVo.class);
        verify(billMapper).insert(billCaptor.capture());
        BillVo saved = billCaptor.getValue();
        assertEquals(USER_ID.toString(), saved.getUserId());
        assertEquals(BillStatus.RECONFIRM.name(), saved.getStatus());
        assertEquals("0.82", saved.getOcrConfidence());
        assertTrue(response.isReconfirmRequired());
        assertEquals(new BigDecimal("0.82"), response.getFieldConfidences().get("amount"));
    }

    @Test
    void rejectsOcrWhenVoiceSessionDoesNotBelongToUser() {
        when(billMapper.existsOwnedVoiceSession(USER_ID.toString(), VOICE_SESSION_ID.toString()))
                .thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class, () -> billService.createOcrDraft(
                USER_ID, VOICE_SESSION_ID, new byte[] {1}, "image/jpeg"));

        assertEquals(ErrorCode.VOICE_SESSION_NOT_FOUND, exception.getErrorCode());
        verify(billOcrClient, never()).analyze(any(), any());
        verify(billMapper, never()).insert(any());
    }

    @Test
    void confirmsMatchingCandidateWithOnlyHashedConfirmationTokenStored() throws Exception {
        when(billMapper.findOwnedByIdForUpdate(USER_ID.toString(), BILL_ID.toString()))
                .thenReturn(draft());
        when(billMapper.confirm(
                        eq(USER_ID.toString()), eq(BILL_ID.toString()), any(), any(), any()))
                .thenReturn(1);

        BillConfirmResponse response = billService.confirm(USER_ID, BILL_ID, confirmationRequest(
                "{\"approved\":true,\"confirmedPayee\":\"한국전력\",\"confirmedAmount\":48200,"
                        + "\"confirmedDueDate\":\"2026-09-25\"}"));

        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(billMapper).confirm(
                eq(USER_ID.toString()), eq(BILL_ID.toString()), hashCaptor.capture(), any(), any());
        assertEquals(BillStatus.CONFIRMED, response.getStatus());
        assertNotNull(response.getConfirmationToken());
        assertEquals(sha256(response.getConfirmationToken()), hashCaptor.getValue());
    }

    @Test
    void keepsCorrectedCandidateInReconfirmStateBeforeIssuingToken() throws Exception {
        when(billMapper.findOwnedByIdForUpdate(USER_ID.toString(), BILL_ID.toString()))
                .thenReturn(draft());
        when(billMapper.updateForReconfirm(
                        eq(USER_ID.toString()),
                        eq(BILL_ID.toString()),
                        eq("한국전력"),
                        eq(50000L),
                        eq(LocalDate.of(2026, 9, 25))))
                .thenReturn(1);

        BillConfirmResponse response = billService.confirm(USER_ID, BILL_ID, confirmationRequest(
                "{\"approved\":true,\"confirmedPayee\":\"한국전력\",\"confirmedAmount\":50000,"
                        + "\"confirmedDueDate\":\"2026-09-25\"}"));

        assertEquals(BillStatus.RECONFIRM, response.getStatus());
        assertFalse(response.isExecutable());
        verify(billMapper, never()).confirm(any(), any(), any(), any(), any());
    }

    @Test
    void executesConfirmedBillOnceAndStoresOnlyMockPaymentRecord() throws Exception {
        String token = "valid-confirmation-token";
        BillVo confirmed = draft();
        confirmed.setStatus(BillStatus.CONFIRMED.name());
        confirmed.setConfirmationTokenHash(sha256(token));
        confirmed.setConfirmationTokenExpiresAt(LocalDateTime.of(2026, 9, 3, 10, 5));
        when(billMapper.findOwnedByIdForUpdate(USER_ID.toString(), BILL_ID.toString()))
                .thenReturn(confirmed);
        when(billMapper.markPaid(USER_ID.toString(), BILL_ID.toString())).thenReturn(1);
        when(idempotencyRecordMapper.complete(any(), any(), any(), eq(200), any(), any())).thenReturn(1);

        BillPaymentResultResponse response = billService.execute(
                USER_ID,
                BILL_ID,
                "bill-payment-key-1",
                executeRequest("{\"confirmationToken\":\"" + token + "\"}"));

        ArgumentCaptor<BillPaymentVo> paymentCaptor = ArgumentCaptor.forClass(BillPaymentVo.class);
        verify(billMapper).insertPayment(paymentCaptor.capture());
        assertEquals("SUCCESS", paymentCaptor.getValue().getStatus());
        assertTrue(paymentCaptor.getValue().getExternalPaymentId().startsWith("MOCK-"));
        assertEquals(BILL_ID, response.getBillId());
        assertEquals("SUCCESS", response.getStatus());
        verify(billMapper).markPaid(USER_ID.toString(), BILL_ID.toString());
    }

    @Test
    void returnsStoredResultForSameIdempotencyKeyWithoutAnotherPayment() throws Exception {
        BillPaymentResultResponse storedResponse = new BillPaymentResultResponse(
                UUID.fromString("30000000-0000-0000-0000-000000000001"),
                BILL_ID,
                "SUCCESS",
                48200L,
                java.time.OffsetDateTime.parse("2026-09-03T10:00:00+09:00"));
        IdempotencyRecordVo idempotencyRecord = new IdempotencyRecordVo();
        idempotencyRecord.setRequestHash(sha256(BILL_ID.toString()));
        idempotencyRecord.setStatus("COMPLETED");
        idempotencyRecord.setResponseBody(objectMapper.writeValueAsString(storedResponse));
        when(idempotencyRecordMapper.findByUserOperationAndKey(
                        USER_ID.toString(), "BILL_PAYMENT_EXECUTE", "bill-payment-key-1"))
                .thenReturn(idempotencyRecord);

        BillPaymentResultResponse response = billService.execute(
                USER_ID,
                BILL_ID,
                "bill-payment-key-1",
                executeRequest("{\"confirmationToken\":\"any-retry-token\"}"));

        assertEquals(storedResponse.getPaymentId(), response.getPaymentId());
        verify(billMapper, never()).insertPayment(any());
    }

    @Test
    void summarizesOnlyActiveBillsDueInRequestedMonth() {
        BillMonthlyAggregateVo aggregate = new BillMonthlyAggregateVo();
        aggregate.setTotalAmount(100_000L);
        aggregate.setPaidAmount(40_000L);
        aggregate.setTotalCount(2);
        aggregate.setPaidCount(1);
        BillVo paid = draft();
        paid.setStatus(BillStatus.PAID.name());
        BillVo outstanding = draft();
        outstanding.setBillId("20000000-0000-0000-0000-000000000002");
        outstanding.setAmount(60_000L);
        outstanding.setStatus(BillStatus.RECONFIRM.name());
        when(billMapper.summarizeOwnedByDueDateRange(
                        USER_ID.toString(), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(aggregate);
        when(billMapper.findOwnedByDueDateRange(
                        USER_ID.toString(), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 20))
                .thenReturn(List.of(paid, outstanding));

        BillMonthlySummaryResponse response = billService.summarizeMonth(USER_ID, 2026, 9);

        assertEquals("2026-09", response.getYearMonth());
        assertEquals(100_000L, response.getTotalAmount());
        assertEquals(40_000L, response.getPaidAmount());
        assertEquals(60_000L, response.getUnpaidAmount());
        assertEquals(2, response.getTotalCount());
        assertEquals(1, response.getPaidCount());
        assertEquals(1, response.getUnpaidCount());
        assertFalse(response.isHasMoreItems());
        assertEquals(2, response.getItems().size());
    }

    @Test
    void defaultsMonthlySummaryToCurrentMonthAndRejectsPartialMonthInput() {
        BillMonthlyAggregateVo empty = new BillMonthlyAggregateVo();
        when(billMapper.summarizeOwnedByDueDateRange(
                        USER_ID.toString(), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(empty);
        when(billMapper.findOwnedByDueDateRange(
                        USER_ID.toString(), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 20))
                .thenReturn(List.of());

        BillMonthlySummaryResponse response = billService.summarizeMonth(USER_ID, null, null);

        assertEquals("2026-09", response.getYearMonth());
        assertEquals(0L, response.getTotalAmount());
        BusinessException exception = assertThrows(
                BusinessException.class, () -> billService.summarizeMonth(USER_ID, 2026, null));
        assertEquals(ErrorCode.INVALID_REQUEST, exception.getErrorCode());
    }

    @Test
    void rejectsYearsOutsideMysqlDateRangeBeforeQueryingBills() {
        BusinessException beforeMysqlRange = assertThrows(
                BusinessException.class, () -> billService.summarizeMonth(USER_ID, 999, 12));
        BusinessException afterMysqlRange = assertThrows(
                BusinessException.class, () -> billService.summarizeMonth(USER_ID, 10_000, 1));

        assertEquals(ErrorCode.INVALID_REQUEST, beforeMysqlRange.getErrorCode());
        assertEquals(ErrorCode.INVALID_REQUEST, afterMysqlRange.getErrorCode());
        verifyNoInteractions(billMapper);
    }

    @Test
    void readsMonthlyAggregateAndItemsInOneRepeatableReadTransaction() throws Exception {
        Transactional transaction = BillServiceImpl.class
                .getMethod("summarizeMonth", UUID.class, Integer.class, Integer.class)
                .getAnnotation(Transactional.class);

        assertNotNull(transaction);
        assertTrue(transaction.readOnly());
        assertEquals(Isolation.REPEATABLE_READ, transaction.isolation());
    }

    private BillOcrCandidate candidate(String amountConfidence) {
        return new BillOcrCandidate(
                "한국전력",
                48200L,
                LocalDate.of(2026, 9, 25),
                "1234-5678",
                Map.of(
                        "payee", new BigDecimal("0.97"),
                        "amount", new BigDecimal(amountConfidence),
                        "dueDate", new BigDecimal("0.96"),
                        "paymentReference", new BigDecimal("0.95")));
    }

    private BillVo draft() {
        BillVo bill = new BillVo();
        bill.setBillId(BILL_ID.toString());
        bill.setUserId(USER_ID.toString());
        bill.setPayee("한국전력");
        bill.setAmount(48200L);
        bill.setDueDate(LocalDate.of(2026, 9, 25));
        bill.setPaymentReference("1234-5678");
        bill.setStatus(BillStatus.DRAFT.name());
        return bill;
    }

    private BillConfirmRequest confirmationRequest(String json) throws Exception {
        return objectMapper.readValue(json, BillConfirmRequest.class);
    }

    private BillExecuteRequest executeRequest(String json) throws Exception {
        return objectMapper.readValue(json, BillExecuteRequest.class);
    }

    private String sha256(String value) throws Exception {
        byte[] bytes = java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte valueByte : bytes) {
            result.append(String.format("%02x", valueByte));
        }
        return result.toString();
    }
}
