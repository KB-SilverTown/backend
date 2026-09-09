package com.silvertown.domain.bill.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BillListResponse {
    private final List<BillSummary> items;
    private final int page;
    private final int size;
    private final long totalCount;
}
