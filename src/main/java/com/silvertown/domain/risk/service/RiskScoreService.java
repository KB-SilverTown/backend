package com.silvertown.domain.risk.service;

import com.silvertown.domain.risk.dto.RiskCheckResponse;
import com.silvertown.domain.risk.dto.RiskScoreResponse;
import java.util.UUID;

public interface RiskScoreService {
    RiskScoreResponse assess(UUID userId, UUID transferId);

    RiskCheckResponse checkContext(UUID userId, UUID transferId, String purposeAnswer);
}
