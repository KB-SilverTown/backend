package com.silvertown.domain.risk.dto;

import java.util.UUID;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RiskCheckRequest {
    @NotNull(message = "송금 ID는 필수입니다.")
    private UUID transferId;

    @Size(max = 500, message = "송금 목적 답변은 500자 이하여야 합니다.")
    private String purposeAnswer;
}
