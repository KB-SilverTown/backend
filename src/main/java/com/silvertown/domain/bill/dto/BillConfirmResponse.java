package com.silvertown.domain.bill.dto;

import com.silvertown.domain.bill.enums.BillStatus;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BillConfirmResponse {
    private final UUID billId;
    private final BillStatus status;
    private final String confirmationToken;
    private final boolean executable;
}
