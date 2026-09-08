package com.silvertown.domain.bill.vo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BillMonthlyAggregateVo {
    private Long totalAmount;
    private Long paidAmount;
    private long totalCount;
    private long paidCount;
}
