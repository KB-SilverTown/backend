package com.silvertown.domain.risk.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RiskScoreResponse {
    private final int preScore;
    private final int score;
    private final String level;
    private final String source;
    private final boolean contextCheckRequired;
    private final List<RiskFactorResponse> factors;
    private final BlacklistCheckResponse blacklistCheck;
    private final String recommendedAction;
    private final boolean additionalCheckRequired;
}
