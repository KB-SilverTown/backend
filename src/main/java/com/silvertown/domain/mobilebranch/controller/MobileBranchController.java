package com.silvertown.domain.mobilebranch.controller;

import com.silvertown.domain.mobilebranch.dto.MobileBranchRecommendationQuery;
import com.silvertown.domain.mobilebranch.dto.MobileBranchRecommendationResponse;
import com.silvertown.domain.mobilebranch.service.MobileBranchService;
import com.silvertown.global.security.AuthenticatedUserId;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Api(tags = "이동점포")
@RestController
@RequestMapping(value = "/api/mobile-branches", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class MobileBranchController {
    private final MobileBranchService mobileBranchService;
    private final AuthenticatedUserId authenticatedUserId;

    @ApiOperation("현재 위치 반경 20km 내 이동점포 방문 일정 추천(최대 5건)")
    @GetMapping("/nearby")
    public ResponseEntity<List<MobileBranchRecommendationResponse>> nearby(
            @RequestParam("latitude") BigDecimal latitude,
            @RequestParam("longitude") BigDecimal longitude,
            Authentication authentication) {
        authenticatedUserId.from(authentication);
        return ResponseEntity.ok(mobileBranchService.recommend(
                new MobileBranchRecommendationQuery(latitude.doubleValue(), longitude.doubleValue(), null)));
    }
}
