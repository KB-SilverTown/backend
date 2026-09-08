package com.silvertown.domain.transfer.dto;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AmountValidationRequest {
    private String transcript;
    private Long recognizedAmount;
    private List<Long> amountCandidates;

    public static AmountValidationRequest of(Long recognizedAmount, List<Long> amountCandidates) {
        AmountValidationRequest request = new AmountValidationRequest();
        request.recognizedAmount = recognizedAmount;
        request.amountCandidates = amountCandidates;
        return request;
    }
}
