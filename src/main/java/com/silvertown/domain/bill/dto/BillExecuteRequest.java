package com.silvertown.domain.bill.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class BillExecuteRequest {
    private String confirmationToken;
}
