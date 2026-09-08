package com.silvertown.domain.transfer.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AmountValidationResponse {
    private final Long recognizedAmount;
    private final List<Long> amountCandidates;
    private final Long confirmedAmount;
    private final boolean amountReconfirmRequired;
}
