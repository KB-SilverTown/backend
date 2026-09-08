package com.silvertown.domain.voice.service.impl;

import com.silvertown.domain.risk.dto.RiskCheckResponse;
import com.silvertown.domain.risk.dto.RiskScoreResponse;
import com.silvertown.domain.risk.service.RiskScoreService;
import com.silvertown.domain.voice.service.RiskAssessmentPort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransferServiceRiskAssessmentAdapter implements RiskAssessmentPort {
    private final RiskScoreService riskScoreService;

    @Override
    public RiskScoreResponse assess(UUID userId, UUID transferId) {
        return riskScoreService.assess(userId, transferId);
    }

    @Override
    public RiskCheckResponse checkContext(UUID userId, UUID transferId, String purposeAnswer) {
        return riskScoreService.checkContext(userId, transferId, purposeAnswer);
    }
}
