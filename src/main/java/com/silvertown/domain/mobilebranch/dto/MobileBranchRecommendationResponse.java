package com.silvertown.domain.mobilebranch.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MobileBranchRecommendationResponse {
    private final String branchId;
    private final String scheduleId;
    private final String name;
    private final String branchType;
    private final String address;
    private final String phone;
    private final long distanceMeters;
    private final LocalDate serviceDate;
    private final LocalDateTime startAt;
    private final LocalDateTime endAt;
    private final List<MobileBranchServiceResponse> services;
}
