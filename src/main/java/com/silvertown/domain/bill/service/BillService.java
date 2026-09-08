package com.silvertown.domain.bill.service;

import com.silvertown.domain.bill.dto.BillConfirmRequest;
import com.silvertown.domain.bill.dto.BillConfirmResponse;
import com.silvertown.domain.bill.dto.BillExecuteRequest;
import com.silvertown.domain.bill.dto.BillListResponse;
import com.silvertown.domain.bill.dto.BillMonthlySummaryResponse;
import com.silvertown.domain.bill.dto.BillOcrResponse;
import com.silvertown.domain.bill.dto.BillPaymentResultResponse;
import com.silvertown.domain.bill.dto.BillResponse;
import java.util.UUID;

public interface BillService {
    BillOcrResponse createOcrDraft(
            UUID userId, UUID voiceSessionId, byte[] imageBytes, String contentType);

    BillListResponse find(UUID userId, String status, Integer page, Integer size);

    BillMonthlySummaryResponse summarizeMonth(UUID userId, Integer year, Integer month);

    BillResponse findById(UUID userId, UUID billId);

    BillConfirmResponse confirm(UUID userId, UUID billId, BillConfirmRequest request);

    BillPaymentResultResponse execute(
            UUID userId, UUID billId, String idempotencyKey, BillExecuteRequest request);
}
