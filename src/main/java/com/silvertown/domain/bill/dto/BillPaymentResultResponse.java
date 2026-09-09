package com.silvertown.domain.bill.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Setter
public class BillPaymentResultResponse {
    private UUID paymentId;
    private UUID billId;
    private String status;
    private Long amount;
    private OffsetDateTime paidAt;
}
