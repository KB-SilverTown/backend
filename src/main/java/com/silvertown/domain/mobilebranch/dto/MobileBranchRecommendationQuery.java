package com.silvertown.domain.mobilebranch.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Internal query input. The HTTP location contract is added after FE agreement. */
@Getter
@AllArgsConstructor
public class MobileBranchRecommendationQuery {
    private final double latitude;
    private final double longitude;
    private final String serviceCode;
}
