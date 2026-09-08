package com.silvertown.domain.risk.vo;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RiskCheck {
    private String riskCheckId;
    private String transferId;
    private String purposeAnswer;
    private boolean suspicious;
    private boolean hold;
    private boolean additionalAuthRequired;
    private String message;
    private OffsetDateTime checkedAt;
}
