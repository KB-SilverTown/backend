package com.silvertown.domain.mobilebranch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.silvertown.domain.mobilebranch.dto.MobileBranchRecommendationQuery;
import com.silvertown.domain.mobilebranch.dto.MobileBranchRecommendationResponse;
import com.silvertown.domain.mobilebranch.mapper.MobileBranchMapper;
import com.silvertown.domain.mobilebranch.service.impl.MobileBranchServiceImpl;
import com.silvertown.domain.mobilebranch.vo.MobileBranchScheduleRow;
import com.silvertown.global.common.exception.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MobileBranchServiceImplTest {
    private static final double EARTH_RADIUS_METERS = 6_371_000d;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-07T00:00:00Z"), ZoneOffset.UTC);

    private MobileBranchMapper mobileBranchMapper;
    private MobileBranchService service;

    @BeforeEach
    void setUp() {
        mobileBranchMapper = Mockito.mock(MobileBranchMapper.class);
        service = new MobileBranchServiceImpl(mobileBranchMapper, CLOCK);
    }

    @Test
    void groupsServicesByVisitAndSortsVisitsByDistance() {
        MobileBranchScheduleRow nearDeposit = row("schedule-near", "branch-near", 37.5000000, 127.0000000);
        nearDeposit.setServiceId("service-deposit");
        nearDeposit.setServiceCode("DEPOSIT");
        nearDeposit.setServiceName("예금 업무");
        nearDeposit.setServiceCategory("ACCOUNT");
        nearDeposit.setPreparationNote("신분증을 지참해 주세요.");

        MobileBranchScheduleRow nearLoan = row("schedule-near", "branch-near", 37.5000000, 127.0000000);
        nearLoan.setServiceId("service-loan");
        nearLoan.setServiceCode("LOAN");
        nearLoan.setServiceName("대출 상담");
        nearLoan.setServiceCategory("LOAN");

        MobileBranchScheduleRow far = row("schedule-far", "branch-far", 37.6000000, 127.0000000);
        when(mobileBranchMapper.findUpcomingActiveSchedules(LocalDateTime.now(CLOCK), "DEPOSIT"))
                .thenReturn(List.of(far, nearDeposit, nearLoan));

        List<MobileBranchRecommendationResponse> responses = service.recommend(
                new MobileBranchRecommendationQuery(37.5000000, 127.0000000, " deposit "));

        assertEquals(2, responses.size());
        assertEquals("schedule-near", responses.get(0).getScheduleId());
        assertEquals(0L, responses.get(0).getDistanceMeters());
        assertEquals(2, responses.get(0).getServices().size());
        assertEquals("DEPOSIT", responses.get(0).getServices().get(0).getServiceCode());
        assertEquals("LOAN", responses.get(0).getServices().get(1).getServiceCode());
        assertEquals("schedule-far", responses.get(1).getScheduleId());
        verify(mobileBranchMapper).findUpcomingActiveSchedules(LocalDateTime.now(CLOCK), "DEPOSIT");
    }

    @Test
    void returnsNoRecommendationWhenNoUpcomingVisitMatchesTheService() {
        when(mobileBranchMapper.findUpcomingActiveSchedules(LocalDateTime.now(CLOCK), "LOAN"))
                .thenReturn(List.of());

        List<MobileBranchRecommendationResponse> responses = service.recommend(
                new MobileBranchRecommendationQuery(37.5, 127.0, "loan"));

        assertEquals(List.of(), responses);
    }

    @Test
    void rejectsOutOfRangeCoordinatesBeforeQueryingTheDatabase() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.recommend(new MobileBranchRecommendationQuery(91d, 127d, null)));

        assertEquals("INVALID_REQUEST", exception.getErrorCode().getCode());
        verify(mobileBranchMapper, never()).findUpcomingActiveSchedules(
                org.mockito.ArgumentMatchers.any(), eq(null));
    }

    @Test
    void filtersOutVisitsBeyondTwentyKilometers() {
        MobileBranchScheduleRow outsideRadius = row(
                "schedule-outside-radius", "branch-outside-radius", 37.8000000, 127.0000000);
        List<MobileBranchScheduleRow> nearbyVisits = IntStream.range(0, 4)
                .mapToObj(index -> row(
                        "schedule-near-" + index,
                        "branch-near-" + index,
                        37.5000000 + index * 0.001d,
                        127.0000000))
                .toList();
        List<MobileBranchScheduleRow> rows = new ArrayList<>(nearbyVisits);
        rows.add(outsideRadius);
        when(mobileBranchMapper.findUpcomingActiveSchedules(LocalDateTime.now(CLOCK), null))
                .thenReturn(rows);

        List<MobileBranchRecommendationResponse> responses = service.recommend(
                new MobileBranchRecommendationQuery(37.5000000, 127.0000000, null));

        assertEquals(4, responses.size());
        assertTrue(responses.stream().anyMatch(response -> response.getScheduleId().equals("schedule-near-0")));
        assertTrue(responses.stream().anyMatch(response -> response.getScheduleId().equals("schedule-near-3")));
        assertFalse(responses.stream().anyMatch(
                response -> response.getScheduleId().equals("schedule-outside-radius")));
    }

    @Test
    void includesVisitsJustInsideAndExcludesVisitsJustOutsideTwentyKilometers() {
        double originLatitude = 37.5000000;
        MobileBranchScheduleRow insideRadius = row(
                "schedule-inside-radius", "branch-inside-radius",
                latitudeAtDistanceMeters(originLatitude, 19_999d), 127.0000000);
        MobileBranchScheduleRow outsideRadius = row(
                "schedule-outside-radius", "branch-outside-radius",
                latitudeAtDistanceMeters(originLatitude, 20_001d), 127.0000000);
        when(mobileBranchMapper.findUpcomingActiveSchedules(LocalDateTime.now(CLOCK), null))
                .thenReturn(List.of(insideRadius, outsideRadius));

        List<MobileBranchRecommendationResponse> responses = service.recommend(
                new MobileBranchRecommendationQuery(originLatitude, 127.0000000, null));

        assertEquals(1, responses.size());
        assertEquals("schedule-inside-radius", responses.get(0).getScheduleId());
        assertEquals(19_999L, responses.get(0).getDistanceMeters());
    }

    @Test
    void limitsRecommendationsToFiveClosestVisitsWithinTwentyKilometers() {
        List<MobileBranchScheduleRow> nearbyVisits = IntStream.range(0, 6)
                .mapToObj(index -> row(
                        "schedule-near-" + index,
                        "branch-near-" + index,
                        37.5000000 + index * 0.001d,
                        127.0000000))
                .toList();
        when(mobileBranchMapper.findUpcomingActiveSchedules(LocalDateTime.now(CLOCK), null))
                .thenReturn(nearbyVisits);

        List<MobileBranchRecommendationResponse> responses = service.recommend(
                new MobileBranchRecommendationQuery(37.5000000, 127.0000000, null));

        assertEquals(5, responses.size());
        assertEquals("schedule-near-0", responses.get(0).getScheduleId());
        assertEquals("schedule-near-4", responses.get(4).getScheduleId());
        assertFalse(responses.stream().anyMatch(response -> response.getScheduleId().equals("schedule-near-5")));
    }

    private MobileBranchScheduleRow row(String scheduleId, String branchId, double latitude, double longitude) {
        MobileBranchScheduleRow row = new MobileBranchScheduleRow();
        row.setScheduleId(scheduleId);
        row.setBranchId(branchId);
        row.setName(branchId + " 이동점포");
        row.setBranchType("MOBILE");
        row.setAddress("서울특별시 테스트로 1");
        row.setLatitude(java.math.BigDecimal.valueOf(latitude));
        row.setLongitude(java.math.BigDecimal.valueOf(longitude));
        row.setServiceDate(LocalDate.of(2026, 9, 8));
        row.setStartAt(LocalDateTime.of(2026, 9, 8, 10, 0));
        row.setEndAt(LocalDateTime.of(2026, 9, 8, 16, 0));
        return row;
    }

    private double latitudeAtDistanceMeters(double originLatitude, double distanceMeters) {
        return originLatitude + Math.toDegrees(distanceMeters / EARTH_RADIUS_METERS);
    }
}
