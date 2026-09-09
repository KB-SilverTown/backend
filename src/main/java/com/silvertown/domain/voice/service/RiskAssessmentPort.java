package com.silvertown.domain.voice.service;

import com.silvertown.domain.risk.dto.RiskCheckResponse;
import com.silvertown.domain.risk.dto.RiskScoreResponse;
import java.util.UUID;

/** Voice-domain boundary for transfer risk assessment and context checking. */
public interface RiskAssessmentPort {
    RiskScoreResponse assess(UUID userId, UUID transferId);

    RiskCheckResponse checkContext(UUID userId, UUID transferId, String purposeAnswer);
}
