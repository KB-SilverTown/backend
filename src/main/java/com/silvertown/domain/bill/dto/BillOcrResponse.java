package com.silvertown.domain.bill.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BillOcrResponse {
    private final UUID billId;
    private final String payee;
    private final Long amount;
    private final LocalDate dueDate;
    private final String paymentReference;
    private final Map<String, BigDecimal> fieldConfidences;
    private final boolean reconfirmRequired;
}
