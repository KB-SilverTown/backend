package com.silvertown.domain.bill.dto;

import com.silvertown.domain.bill.enums.BillStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BillResponse {
    private final UUID billId;
    private final BillStatus status;
    private final String payee;
    private final Long amount;
    private final LocalDate dueDate;
    private final String paymentReference;
    private final Map<String, BigDecimal> fieldConfidences;
    private final boolean reconfirmRequired;
}
