package com.silvertown.domain.risk.controller;

import com.silvertown.domain.risk.dto.RiskCheckRequest;
import com.silvertown.domain.risk.dto.RiskCheckResponse;
import com.silvertown.domain.risk.dto.RiskScoreRequest;
import com.silvertown.domain.risk.dto.RiskScoreResponse;
import com.silvertown.domain.risk.service.RiskScoreService;
import com.silvertown.global.security.AuthenticatedUserId;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Api(tags = "안심 송금")
@RestController
@RequestMapping(value = "/api/transfers", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class RiskScoreController {
    private final RiskScoreService riskScoreService;
    private final AuthenticatedUserId authenticatedUserId;

    @ApiOperation("거래 Risk Score 산출")
    @PostMapping("/risk-score")
    public ResponseEntity<RiskScoreResponse> assess(
            @Valid @RequestBody RiskScoreRequest request, Authentication authentication) {
        return ResponseEntity.ok(riskScoreService.assess(
                authenticatedUserId.from(authentication), request.getTransferId()));
    }

    @ApiOperation("고위험 거래 추가 확인")
    @PostMapping("/risk-check")
    public ResponseEntity<RiskCheckResponse> check(
            @Valid @RequestBody RiskCheckRequest request, Authentication authentication) {
        return ResponseEntity.ok(riskScoreService.checkContext(
                authenticatedUserId.from(authentication),
                request.getTransferId(),
                request.getPurposeAnswer()));
    }
}
