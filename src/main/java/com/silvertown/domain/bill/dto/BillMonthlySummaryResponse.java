package com.silvertown.domain.bill.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BillMonthlySummaryResponse {
    private final String yearMonth;
    private final long totalAmount;
    private final long paidAmount;
    private final long unpaidAmount;
    private final long totalCount;
    private final long paidCount;
    private final long unpaidCount;
    private final boolean hasMoreItems;
    private final List<BillSummary> items;
}
