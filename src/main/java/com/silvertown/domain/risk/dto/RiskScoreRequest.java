package com.silvertown.domain.risk.dto;

import java.util.UUID;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RiskScoreRequest {
    @NotNull(message = "송금 ID는 필수입니다.")
    private UUID transferId;
}
