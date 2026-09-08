package com.silvertown.domain.risk.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RiskFactorResponse {
    private final String code;
    private final String label;
    private final int weight;
}
