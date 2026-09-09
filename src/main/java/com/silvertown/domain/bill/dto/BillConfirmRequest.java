package com.silvertown.domain.bill.dto;

import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class BillConfirmRequest {
    private Boolean approved;
    private String confirmedPayee;
    private Long confirmedAmount;
    private LocalDate confirmedDueDate;
}
