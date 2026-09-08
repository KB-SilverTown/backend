package com.silvertown.domain.bill.client;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record BillOcrCandidate(
        String payee,
        Long amount,
        LocalDate dueDate,
        String paymentReference,
        Map<String, BigDecimal> fieldConfidences) {}
