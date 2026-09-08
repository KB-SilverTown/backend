package com.silvertown.domain.risk.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RiskCheckResponse {
    private final int score;
    private final String level;
    private final boolean suspicious;
    private final boolean hold;
    private final boolean verificationRequired;
    private final boolean additionalCheckRequired;
    private final String warningText;
    private final String warningTtsText;
    private final String maskedEmergencyContact;
    private final List<RiskFactorResponse> reasons;
}
