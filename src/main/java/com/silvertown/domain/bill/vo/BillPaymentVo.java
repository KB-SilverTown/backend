package com.silvertown.domain.bill.vo;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class BillPaymentVo {
    private String paymentId;
    private String billId;
    private Long amount;
    private String status;
    private String externalPaymentId;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
