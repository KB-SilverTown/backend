package com.silvertown.domain.risk.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BlacklistCheckResponse {
    private final String provider;
    private final String matchStatus;
}
