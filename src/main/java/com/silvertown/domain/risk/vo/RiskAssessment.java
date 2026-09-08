package com.silvertown.domain.risk.vo;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RiskAssessment {
    private String assessmentId;
    private String transferId;
    private String source;
    private int score;
    private String level;
    private String factors;
    private boolean additionalCheckRequired;
    private OffsetDateTime assessedAt;
}
