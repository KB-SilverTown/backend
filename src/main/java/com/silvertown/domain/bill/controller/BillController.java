package com.silvertown.domain.bill.controller;

import com.silvertown.domain.bill.dto.BillConfirmRequest;
import com.silvertown.domain.bill.dto.BillConfirmResponse;
import com.silvertown.domain.bill.dto.BillExecuteRequest;
import com.silvertown.domain.bill.dto.BillListResponse;
import com.silvertown.domain.bill.dto.BillMonthlySummaryResponse;
import com.silvertown.domain.bill.dto.BillOcrResponse;
import com.silvertown.domain.bill.dto.BillPaymentResultResponse;
import com.silvertown.domain.bill.dto.BillResponse;
import com.silvertown.domain.bill.service.BillService;
import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import com.silvertown.global.security.AuthenticatedUserId;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Api(tags = "고지서 납부")
@RestController
@RequestMapping(value = "/api/bills", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class BillController {
    private final BillService billService;
    private final AuthenticatedUserId authenticatedUserId;

    @ApiOperation("고지서 OCR 후보 생성")
    @ApiResponses({
            @ApiResponse(code = 400, message = "고지서 이미지가 올바르지 않음"),
            @ApiResponse(code = 404, message = "음성 세션을 찾을 수 없음"),
            @ApiResponse(code = 502, message = "OCR 처리 실패")
    })
    @PostMapping(value = "/ocr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BillOcrResponse> createOcrDraft(
            @RequestParam("image") MultipartFile image,
            @RequestParam("voiceSessionId") UUID voiceSessionId,
            Authentication authentication) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(billService.createOcrDraft(
                    authenticatedUserId.from(authentication),
                    voiceSessionId,
                    image.getBytes(),
                    image.getContentType()));
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.BILL_IMAGE_INVALID);
        }
    }

    @ApiOperation("내 고지서 목록 조회")
    @GetMapping
    public ResponseEntity<BillListResponse> find(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            Authentication authentication) {
        return ResponseEntity.ok(billService.find(
                authenticatedUserId.from(authentication), status, page, size));
    }

    @ApiOperation("월별 공과금 요약 조회")
    @GetMapping("/monthly-summary")
    public ResponseEntity<BillMonthlySummaryResponse> summarizeMonth(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            Authentication authentication) {
        return ResponseEntity.ok(billService.summarizeMonth(
                authenticatedUserId.from(authentication), year, month));
    }

    @ApiOperation("고지서 상세 조회")
    @GetMapping("/{billId}")
    public ResponseEntity<BillResponse> findById(
            @PathVariable UUID billId, Authentication authentication) {
        return ResponseEntity.ok(billService.findById(authenticatedUserId.from(authentication), billId));
    }

    @ApiOperation("고지서 납부정보 확인")
    @PostMapping(value = "/{billId}/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BillConfirmResponse> confirm(
            @PathVariable UUID billId,
            @RequestBody BillConfirmRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(billService.confirm(
                authenticatedUserId.from(authentication), billId, request));
    }

    @ApiOperation("고지서 모의 납부 실행")
    @ApiResponses({
            @ApiResponse(code = 400, message = "확인 토큰 또는 멱등성 키가 올바르지 않음"),
            @ApiResponse(code = 409, message = "중복 또는 현재 상태에서 실행할 수 없음")
    })
    @PostMapping(value = "/{billId}/execute", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BillPaymentResultResponse> execute(
            @PathVariable UUID billId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody BillExecuteRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(billService.execute(
                authenticatedUserId.from(authentication), billId, idempotencyKey, request));
    }
}
