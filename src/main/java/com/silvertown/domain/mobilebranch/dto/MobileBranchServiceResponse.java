package com.silvertown.domain.mobilebranch.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MobileBranchServiceResponse {
    private final String serviceId;
    private final String serviceCode;
    private final String serviceName;
    private final String category;
    private final String preparationNote;
}
