package com.silvertown.domain.bill.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvertown.domain.bill.client.BillOcrCandidate;
import com.silvertown.domain.bill.client.BillOcrClient;
import com.silvertown.domain.bill.dto.BillConfirmRequest;
import com.silvertown.domain.bill.dto.BillConfirmResponse;
import com.silvertown.domain.bill.dto.BillExecuteRequest;
import com.silvertown.domain.bill.dto.BillListResponse;
import com.silvertown.domain.bill.dto.BillMonthlySummaryResponse;
import com.silvertown.domain.bill.dto.BillOcrResponse;
import com.silvertown.domain.bill.dto.BillPaymentResultResponse;
import com.silvertown.domain.bill.dto.BillResponse;
import com.silvertown.domain.bill.dto.BillSummary;
import com.silvertown.domain.bill.enums.BillStatus;
import com.silvertown.domain.bill.mapper.BillMapper;
import com.silvertown.domain.bill.service.BillService;
import com.silvertown.domain.bill.vo.BillVo;
import com.silvertown.domain.bill.vo.BillPaymentVo;
import com.silvertown.domain.bill.vo.BillMonthlyAggregateVo;
import com.silvertown.domain.voice.mapper.IdempotencyRecordMapper;
import com.silvertown.domain.voice.vo.IdempotencyRecordVo;
import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillServiceImpl implements BillService {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MONTHLY_SUMMARY_ITEM_LIMIT = 20;
    private static final int MYSQL_MIN_YEAR = 1000;
    private static final int MYSQL_MAX_YEAR = 9999;
    private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    private static final BigDecimal RECONFIRM_THRESHOLD = new BigDecimal("0.90");
    private static final String[] CORE_FIELDS = {"payee", "amount", "dueDate", "paymentReference"};
    private static final String BILL_EXECUTE_OPERATION = "BILL_PAYMENT_EXECUTE";
    private static final String COMPLETED = "COMPLETED";
    private static final long IDEMPOTENCY_TTL_SECONDS = 24 * 60 * 60;
    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();

    private final BillMapper billMapper;
    private final BillOcrClient billOcrClient;
    private final IdempotencyRecordMapper idempotencyRecordMapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final long confirmationTokenTtlSeconds;

    public BillServiceImpl(
            BillMapper billMapper,
            BillOcrClient billOcrClient,
            IdempotencyRecordMapper idempotencyRecordMapper,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${bill.confirmation-token-ttl-seconds:300}") long confirmationTokenTtlSeconds) {
        this.billMapper = billMapper;
        this.billOcrClient = billOcrClient;
        this.idempotencyRecordMapper = idempotencyRecordMapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.confirmationTokenTtlSeconds = confirmationTokenTtlSeconds;
    }

    @Override
    public BillOcrResponse createOcrDraft(
            UUID userId, UUID voiceSessionId, byte[] imageBytes, String contentType) {
        validateImage(imageBytes, contentType);
        if (voiceSessionId == null || billMapper.existsOwnedVoiceSession(
                userId.toString(), voiceSessionId.toString()) == 0) {
            throw new BusinessException(ErrorCode.VOICE_SESSION_NOT_FOUND);
        }
        BillOcrCandidate candidate = billOcrClient.analyze(imageBytes, contentType);
        Map<String, BigDecimal> confidences = normalizedConfidences(candidate);
        boolean reconfirmRequired = requiresReconfirm(candidate, confidences);
        BillVo bill = new BillVo();
        bill.setBillId(UUID.randomUUID().toString());
        bill.setUserId(userId.toString());
        bill.setPayee(candidate.payee());
        bill.setAmount(candidate.amount());
        bill.setDueDate(candidate.dueDate());
        bill.setPaymentReference(candidate.paymentReference());
        bill.setOcrConfidence(minConfidence(confidences).toPlainString());
        bill.setFieldConfidences(writeConfidences(confidences));
        bill.setStatus(reconfirmRequired ? BillStatus.RECONFIRM.name() : BillStatus.DRAFT.name());
        billMapper.insert(bill);
        return new BillOcrResponse(
                UUID.fromString(bill.getBillId()),
                bill.getPayee(),
                bill.getAmount(),
                bill.getDueDate(),
                bill.getPaymentReference(),
                confidences,
                reconfirmRequired);
    }

    @Override
    public BillListResponse find(UUID userId, String status, Integer page, Integer size) {
        BillStatus billStatus = parseStatus(status);
        int resolvedPage = page == null ? DEFAULT_PAGE : page;
        int resolvedSize = size == null ? DEFAULT_SIZE : size;
        if (resolvedPage < 0 || resolvedSize < 1 || resolvedSize > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        long totalCount = billMapper.countOwnedByCondition(
                userId.toString(), billStatus == null ? null : billStatus.name());
        if (resolvedPage > Integer.MAX_VALUE / resolvedSize) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        int offset = resolvedPage * resolvedSize;
        List<BillSummary> items = billMapper.findOwnedByCondition(
                        userId.toString(), billStatus == null ? null : billStatus.name(), offset, resolvedSize)
                .stream()
                .map(this::toSummary)
                .collect(Collectors.toList());
        return new BillListResponse(items, resolvedPage, resolvedSize, totalCount);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public BillMonthlySummaryResponse summarizeMonth(UUID userId, Integer year, Integer month) {
        YearMonth targetMonth = resolveTargetMonth(year, month);
        LocalDate startDueDate = targetMonth.atDay(1);
        LocalDate lastDueDate = targetMonth.atEndOfMonth();
        BillMonthlyAggregateVo aggregate = billMapper.summarizeOwnedByDueDateRange(
                userId.toString(), startDueDate, lastDueDate);
        if (aggregate == null) {
            aggregate = new BillMonthlyAggregateVo();
        }

        long totalAmount = valueOrZero(aggregate.getTotalAmount());
        long paidAmount = valueOrZero(aggregate.getPaidAmount());
        long totalCount = aggregate.getTotalCount();
        long paidCount = aggregate.getPaidCount();
        List<BillSummary> items = billMapper.findOwnedByDueDateRange(
                        userId.toString(), startDueDate, lastDueDate, MONTHLY_SUMMARY_ITEM_LIMIT)
                .stream()
                .map(this::toSummary)
                .collect(Collectors.toList());
        return new BillMonthlySummaryResponse(
                targetMonth.toString(),
                totalAmount,
                paidAmount,
                totalAmount - paidAmount,
                totalCount,
                paidCount,
                totalCount - paidCount,
                totalCount > items.size(),
                items);
    }

    @Override
    public BillResponse findById(UUID userId, UUID billId) {
        return toResponse(findOwned(userId, billId));
    }

    @Override
    @Transactional
    public BillConfirmResponse confirm(UUID userId, UUID billId, BillConfirmRequest request) {
        if (request == null || request.getApproved() == null) {
            throw new BusinessException(ErrorCode.BILL_CONFIRMATION_INVALID);
        }
        BillVo bill = findOwnedForUpdate(userId, billId);
        BillStatus status = parseStatus(bill.getStatus());
        if (status != BillStatus.DRAFT && status != BillStatus.RECONFIRM) {
            throw new BusinessException(ErrorCode.BILL_INVALID_STATE);
        }
        if (!request.getApproved()) {
            if (billMapper.cancelForOwner(userId.toString(), billId.toString()) != 1) {
                throw new BusinessException(ErrorCode.BILL_INVALID_STATE);
            }
            return new BillConfirmResponse(billId, BillStatus.CANCELLED, null, false);
        }
        validateConfirmation(request);
        if (!matchesCandidate(bill, request)) {
            if (billMapper.updateForReconfirm(
                    userId.toString(),
                    billId.toString(),
                    request.getConfirmedPayee().trim(),
                    request.getConfirmedAmount(),
                    request.getConfirmedDueDate()) != 1) {
                throw new BusinessException(ErrorCode.BILL_INVALID_STATE);
            }
            return new BillConfirmResponse(billId, BillStatus.RECONFIRM, null, false);
        }
        String confirmationToken = newConfirmationToken();
        LocalDateTime now = LocalDateTime.now(clock);
        if (confirmationTokenTtlSeconds <= 0 || billMapper.confirm(
                userId.toString(),
                billId.toString(),
                sha256(confirmationToken),
                now.plusSeconds(confirmationTokenTtlSeconds),
                now) != 1) {
            throw new BusinessException(ErrorCode.BILL_INVALID_STATE);
        }
        return new BillConfirmResponse(billId, BillStatus.CONFIRMED, confirmationToken, true);
    }

    @Override
    @Transactional
    public BillPaymentResultResponse execute(
            UUID userId, UUID billId, String idempotencyKey, BillExecuteRequest request) {
        validateExecuteRequest(idempotencyKey, request);
        String requestHash = sha256(billId.toString());
        IdempotencyRecordVo existing = idempotencyRecordMapper.findByUserOperationAndKey(
                userId.toString(), BILL_EXECUTE_OPERATION, idempotencyKey);
        if (existing != null) {
            return existingPaymentResult(existing, requestHash);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        IdempotencyRecordVo idempotencyRecord = new IdempotencyRecordVo();
        idempotencyRecord.setIdempotencyRecordId(UUID.randomUUID().toString());
        idempotencyRecord.setUserId(userId.toString());
        idempotencyRecord.setOperation(BILL_EXECUTE_OPERATION);
        idempotencyRecord.setIdempotencyKey(idempotencyKey);
        idempotencyRecord.setRequestHash(requestHash);
        idempotencyRecord.setStatus("IN_PROGRESS");
        idempotencyRecord.setExpiresAt(now.plusSeconds(IDEMPOTENCY_TTL_SECONDS));
        try {
            idempotencyRecordMapper.insert(idempotencyRecord);
        } catch (DuplicateKeyException exception) {
            IdempotencyRecordVo concurrent = idempotencyRecordMapper.findByUserOperationAndKey(
                    userId.toString(), BILL_EXECUTE_OPERATION, idempotencyKey);
            if (concurrent != null) {
                return existingPaymentResult(concurrent, requestHash);
            }
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }

        BillVo bill = findOwnedForUpdate(userId, billId);
        if (parseStatus(bill.getStatus()) != BillStatus.CONFIRMED
                || isBlank(bill.getConfirmationTokenHash())
                || bill.getConfirmationTokenExpiresAt() == null
                || !bill.getConfirmationTokenExpiresAt().isAfter(now)
                || !MessageDigest.isEqual(
                        bill.getConfirmationTokenHash().getBytes(StandardCharsets.UTF_8),
                        sha256(request.getConfirmationToken()).getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ErrorCode.BILL_CONFIRMATION_INVALID);
        }

        BillPaymentVo payment = new BillPaymentVo();
        payment.setPaymentId(UUID.randomUUID().toString());
        payment.setBillId(billId.toString());
        payment.setAmount(bill.getAmount());
        payment.setStatus("SUCCESS");
        payment.setExternalPaymentId("MOCK-" + UUID.randomUUID());
        payment.setPaidAt(now);
        billMapper.insertPayment(payment);
        if (billMapper.markPaid(userId.toString(), billId.toString()) != 1) {
            throw new BusinessException(ErrorCode.BILL_INVALID_STATE);
        }

        BillPaymentResultResponse response = paymentResult(payment);
        if (idempotencyRecordMapper.complete(
                idempotencyRecord.getIdempotencyRecordId(),
                "BILL_PAYMENT",
                payment.getPaymentId(),
                200,
                writePaymentResult(response),
                now) != 1) {
            throw new BusinessException(ErrorCode.BILL_INVALID_STATE);
        }
        return response;
    }

    private void validateImage(byte[] imageBytes, String contentType) {
        if (imageBytes == null || imageBytes.length == 0 || imageBytes.length > MAX_IMAGE_BYTES
                || !("image/jpeg".equalsIgnoreCase(contentType)
                || "image/png".equalsIgnoreCase(contentType)
                || "image/webp".equalsIgnoreCase(contentType))) {
            throw new BusinessException(ErrorCode.BILL_IMAGE_INVALID);
        }
    }

    private void validateExecuteRequest(String idempotencyKey, BillExecuteRequest request) {
        if (isBlank(idempotencyKey)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
        if (idempotencyKey.length() > 255 || request == null || isBlank(request.getConfirmationToken())) {
            throw new BusinessException(ErrorCode.BILL_CONFIRMATION_INVALID);
        }
    }

    private BillPaymentResultResponse existingPaymentResult(
            IdempotencyRecordVo idempotencyRecord, String requestHash) {
        if (!requestHash.equals(idempotencyRecord.getRequestHash())) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }
        if (!COMPLETED.equals(idempotencyRecord.getStatus()) || isBlank(idempotencyRecord.getResponseBody())) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }
        try {
            return objectMapper.readValue(idempotencyRecord.getResponseBody(), BillPaymentResultResponse.class);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.BILL_INVALID_STATE);
        }
    }

    private BillPaymentResultResponse paymentResult(BillPaymentVo payment) {
        OffsetDateTime paidAt = payment.getPaidAt().atZone(clock.getZone()).toOffsetDateTime();
        return new BillPaymentResultResponse(
                UUID.fromString(payment.getPaymentId()),
                UUID.fromString(payment.getBillId()),
                payment.getStatus(),
                payment.getAmount(),
                paidAt);
    }

    private String writePaymentResult(BillPaymentResultResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.BILL_INVALID_STATE);
        }
    }

    private Map<String, BigDecimal> normalizedConfidences(BillOcrCandidate candidate) {
        if (candidate == null || candidate.fieldConfidences() == null) {
            throw new BusinessException(ErrorCode.BILL_OCR_FAILED);
        }
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (String field : CORE_FIELDS) {
            BigDecimal confidence = candidate.fieldConfidences().get(field);
            if (confidence == null || confidence.compareTo(BigDecimal.ZERO) < 0
                    || confidence.compareTo(BigDecimal.ONE) > 0) {
                throw new BusinessException(ErrorCode.BILL_OCR_FAILED);
            }
            result.put(field, confidence);
        }
        return result;
    }

    private boolean requiresReconfirm(BillOcrCandidate candidate, Map<String, BigDecimal> confidences) {
        return isBlank(candidate.payee())
                || candidate.amount() == null
                || candidate.amount() <= 0
                || candidate.dueDate() == null
                || isBlank(candidate.paymentReference())
                || confidences.values().stream().anyMatch(value -> value.compareTo(RECONFIRM_THRESHOLD) < 0);
    }

    private BigDecimal minConfidence(Map<String, BigDecimal> confidences) {
        return confidences.values().stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
    }

    private String writeConfidences(Map<String, BigDecimal> confidences) {
        try {
            return objectMapper.writeValueAsString(confidences);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.BILL_OCR_FAILED);
        }
    }

    private BillStatus parseStatus(String status) {
        if (isBlank(status)) {
            return null;
        }
        try {
            return BillStatus.valueOf(status);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    private YearMonth resolveTargetMonth(Integer year, Integer month) {
        if (year == null && month == null) {
            return YearMonth.now(clock);
        }
        if (year == null || month == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        try {
            YearMonth targetMonth = YearMonth.of(year, month);
            if (targetMonth.getYear() < MYSQL_MIN_YEAR || targetMonth.getYear() > MYSQL_MAX_YEAR) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
            return targetMonth;
        } catch (java.time.DateTimeException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

    private BillVo findOwned(UUID userId, UUID billId) {
        BillVo bill = billMapper.findOwnedById(userId.toString(), billId.toString());
        if (bill == null) {
            throw new BusinessException(ErrorCode.BILL_NOT_FOUND);
        }
        return bill;
    }

    private BillVo findOwnedForUpdate(UUID userId, UUID billId) {
        BillVo bill = billMapper.findOwnedByIdForUpdate(userId.toString(), billId.toString());
        if (bill == null) {
            throw new BusinessException(ErrorCode.BILL_NOT_FOUND);
        }
        return bill;
    }

    private BillSummary toSummary(BillVo bill) {
        return new BillSummary(
                UUID.fromString(bill.getBillId()),
                parseStatus(bill.getStatus()),
                bill.getPayee(),
                bill.getAmount(),
                bill.getDueDate(),
                parseStatus(bill.getStatus()) == BillStatus.RECONFIRM);
    }

    private BillResponse toResponse(BillVo bill) {
        BillStatus status = parseStatus(bill.getStatus());
        return new BillResponse(
                UUID.fromString(bill.getBillId()),
                status,
                bill.getPayee(),
                bill.getAmount(),
                bill.getDueDate(),
                bill.getPaymentReference(),
                readConfidences(bill.getFieldConfidences()),
                status == BillStatus.RECONFIRM);
    }

    private Map<String, BigDecimal> readConfidences(String value) {
        if (isBlank(value)) {
            return Map.of();
        }
        try {
            Map<String, BigDecimal> confidences = objectMapper.readValue(
                    value, new TypeReference<Map<String, BigDecimal>>() {});
            return Map.copyOf(confidences);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.BILL_OCR_FAILED);
        }
    }

    private void validateConfirmation(BillConfirmRequest request) {
        if (isBlank(request.getConfirmedPayee())
                || request.getConfirmedAmount() == null
                || request.getConfirmedAmount() <= 0
                || request.getConfirmedDueDate() == null) {
            throw new BusinessException(ErrorCode.BILL_CONFIRMATION_INVALID);
        }
    }

    private boolean matchesCandidate(BillVo bill, BillConfirmRequest request) {
        return request.getConfirmedPayee().trim().equals(bill.getPayee())
                && request.getConfirmedAmount().equals(bill.getAmount())
                && request.getConfirmedDueDate().equals(bill.getDueDate());
    }

    private String newConfirmationToken() {
        byte[] bytes = new byte[32];
        TOKEN_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte valueByte : hash) {
                result.append(String.format("%02x", valueByte));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
