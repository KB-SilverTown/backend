package com.silvertown.domain.mobilebranch.service.impl;

import com.silvertown.domain.mobilebranch.dto.MobileBranchRecommendationQuery;
import com.silvertown.domain.mobilebranch.dto.MobileBranchRecommendationResponse;
import com.silvertown.domain.mobilebranch.dto.MobileBranchServiceResponse;
import com.silvertown.domain.mobilebranch.mapper.MobileBranchMapper;
import com.silvertown.domain.mobilebranch.service.MobileBranchService;
import com.silvertown.domain.mobilebranch.vo.MobileBranchScheduleRow;
import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MobileBranchServiceImpl implements MobileBranchService {
    private static final double EARTH_RADIUS_METERS = 6_371_000d;
    private static final long MAX_NEARBY_DISTANCE_METERS = 20_000L;
    private static final int MAX_RECOMMENDATION_COUNT = 5;

    private final MobileBranchMapper mobileBranchMapper;
    private final Clock clock;

    @Override
    public List<MobileBranchRecommendationResponse> recommend(MobileBranchRecommendationQuery query) {
        validate(query);
        String serviceCode = normalizeServiceCode(query.getServiceCode());
        LocalDateTime now = LocalDateTime.now(clock);
        List<MobileBranchScheduleRow> rows = mobileBranchMapper.findUpcomingActiveSchedules(now, serviceCode);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        Map<String, RecommendationAccumulator> recommendations = new LinkedHashMap<>();
        for (MobileBranchScheduleRow row : rows) {
            if (!isCompleteSchedule(row)) {
                continue;
            }
            RecommendationAccumulator accumulator = recommendations.computeIfAbsent(
                    row.getScheduleId(), ignored -> RecommendationAccumulator.from(row, distanceMeters(query, row)));
            accumulator.addService(row);
        }

        return recommendations.values().stream()
                .filter(recommendation -> recommendation.distanceMeters <= MAX_NEARBY_DISTANCE_METERS)
                .map(RecommendationAccumulator::toResponse)
                .sorted(Comparator.comparingLong(MobileBranchRecommendationResponse::getDistanceMeters)
                        .thenComparing(MobileBranchRecommendationResponse::getStartAt)
                        .thenComparing(MobileBranchRecommendationResponse::getBranchId))
                .limit(MAX_RECOMMENDATION_COUNT)
                .toList();
    }

    private void validate(MobileBranchRecommendationQuery query) {
        if (query == null || !Double.isFinite(query.getLatitude()) || !Double.isFinite(query.getLongitude())
                || query.getLatitude() < -90d || query.getLatitude() > 90d
                || query.getLongitude() < -180d || query.getLongitude() > 180d) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    private String normalizeServiceCode(String serviceCode) {
        if (serviceCode == null || serviceCode.trim().isEmpty()) {
            return null;
        }
        return serviceCode.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isCompleteSchedule(MobileBranchScheduleRow row) {
        return row != null && row.getBranchId() != null && row.getScheduleId() != null
                && row.getName() != null && row.getLatitude() != null && row.getLongitude() != null
                && row.getServiceDate() != null && row.getStartAt() != null && row.getEndAt() != null;
    }

    private long distanceMeters(MobileBranchRecommendationQuery query, MobileBranchScheduleRow row) {
        double latitudeRadians = Math.toRadians(row.getLatitude().doubleValue() - query.getLatitude());
        double longitudeRadians = Math.toRadians(row.getLongitude().doubleValue() - query.getLongitude());
        double sourceLatitudeRadians = Math.toRadians(query.getLatitude());
        double branchLatitudeRadians = Math.toRadians(row.getLatitude().doubleValue());
        double haversine = Math.sin(latitudeRadians / 2d) * Math.sin(latitudeRadians / 2d)
                + Math.cos(sourceLatitudeRadians) * Math.cos(branchLatitudeRadians)
                * Math.sin(longitudeRadians / 2d) * Math.sin(longitudeRadians / 2d);
        double boundedHaversine = Math.max(0d, Math.min(1d, haversine));
        return Math.round(EARTH_RADIUS_METERS * 2d * Math.atan2(
                Math.sqrt(boundedHaversine), Math.sqrt(1d - boundedHaversine)));
    }

    private static class RecommendationAccumulator {
        private final MobileBranchScheduleRow row;
        private final long distanceMeters;
        private final List<MobileBranchServiceResponse> services = new ArrayList<>();
        private final Set<String> serviceIds = new LinkedHashSet<>();

        private RecommendationAccumulator(MobileBranchScheduleRow row, long distanceMeters) {
            this.row = row;
            this.distanceMeters = distanceMeters;
        }

        static RecommendationAccumulator from(MobileBranchScheduleRow row, long distanceMeters) {
            return new RecommendationAccumulator(row, distanceMeters);
        }

        void addService(MobileBranchScheduleRow serviceRow) {
            if (serviceRow.getServiceId() == null || !serviceIds.add(serviceRow.getServiceId())) {
                return;
            }
            services.add(new MobileBranchServiceResponse(
                    serviceRow.getServiceId(), serviceRow.getServiceCode(), serviceRow.getServiceName(),
                    serviceRow.getServiceCategory(), serviceRow.getPreparationNote()));
        }

        MobileBranchRecommendationResponse toResponse() {
            return new MobileBranchRecommendationResponse(
                    row.getBranchId(), row.getScheduleId(), row.getName(), row.getBranchType(), row.getAddress(),
                    row.getPhone(), distanceMeters, row.getServiceDate(), row.getStartAt(), row.getEndAt(),
                    List.copyOf(services));
        }
    }
}
