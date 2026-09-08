package com.silvertown.domain.bill.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class BillVo {
    private String billId;
    private String userId;
    private String payee;
    private Long amount;
    private LocalDate dueDate;
    private String paymentReference;
    private String ocrConfidence;
    private String fieldConfidences;
    private String status;
    private String confirmationTokenHash;
    private LocalDateTime confirmationTokenExpiresAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
