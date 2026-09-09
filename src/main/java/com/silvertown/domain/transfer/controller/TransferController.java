package com.silvertown.domain.transfer.controller;

import com.silvertown.domain.transfer.dto.AmountValidationRequest;
import com.silvertown.domain.transfer.dto.AmountValidationResponse;
import com.silvertown.domain.transfer.dto.TransferPrepareRequest;
import com.silvertown.domain.transfer.dto.TransferPinRequest;
import com.silvertown.domain.transfer.dto.TransferAuthenticationResponse;
import com.silvertown.domain.transfer.dto.TransferResultResponse;
import com.silvertown.domain.transfer.dto.TransferPrepareResponse;
import com.silvertown.domain.transfer.dto.TransferConfirmRequest;
import com.silvertown.domain.transfer.dto.TransferConfirmResponse;
import com.silvertown.domain.transfer.dto.TransferResponse;
import com.silvertown.domain.transfer.dto.GuardianVerificationStartRequest;
import com.silvertown.domain.transfer.dto.GuardianVerificationStartResponse;
import com.silvertown.domain.transfer.dto.GuardianVerificationConfirmRequest;
import com.silvertown.domain.transfer.dto.GuardianVerificationConfirmResponse;
import com.silvertown.domain.transfer.service.TransferService;
import com.silvertown.global.security.AuthenticatedUserId;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.UUID;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Api(tags = "송금")
@RestController
@RequestMapping(value = "/api/transfers", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class TransferController {
    private final TransferService transferService;
    private final AuthenticatedUserId authenticatedUserId;

    @ApiOperation("송금 금액 검증")
    @PostMapping("/validate-amount")
    public ResponseEntity<AmountValidationResponse> validateAmount(
            @RequestBody AmountValidationRequest request, Authentication authentication) {
        authenticatedUserId.from(authentication);
        return ResponseEntity.ok(transferService.validateAmount(request));
    }

    @ApiOperation("송금 초안 생성")
    @PostMapping("/prepare")
    public ResponseEntity<TransferPrepareResponse> prepare(
            @Valid @RequestBody TransferPrepareRequest request, Authentication authentication) {
        return ResponseEntity.status(201).body(
                transferService.prepare(authenticatedUserId.from(authentication), request));
    }

    @ApiOperation("송금 상태 조회")
    @GetMapping("/{transferId}")
    public ResponseEntity<TransferResponse> get(
            @PathVariable UUID transferId, Authentication authentication) {
        return ResponseEntity.ok(
                transferService.get(authenticatedUserId.from(authentication), transferId));
    }

    @ApiOperation("송금 최종 승인")
    @PostMapping("/{transferId}/confirm")
    public ResponseEntity<TransferConfirmResponse> confirm(
            @PathVariable UUID transferId,
            @Valid @RequestBody TransferConfirmRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(transferService.confirm(
                authenticatedUserId.from(authentication), transferId, request));
    }

    @ApiOperation("보호자 인증번호 발송")
    @PostMapping("/{transferId}/guardian-verifications")
    public ResponseEntity<GuardianVerificationStartResponse> startGuardianVerification(
            @PathVariable UUID transferId,
            @RequestBody(required = false) GuardianVerificationStartRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(transferService.startGuardianVerification(
                authenticatedUserId.from(authentication), transferId,
                request == null ? new GuardianVerificationStartRequest() : request));
    }

    @ApiOperation("보호자 인증번호 확인")
    @PostMapping("/{transferId}/guardian-verifications/{verificationId}/verify")
    public ResponseEntity<GuardianVerificationConfirmResponse> verifyGuardianVerification(
            @PathVariable UUID transferId, @PathVariable UUID verificationId,
            @Valid @RequestBody GuardianVerificationConfirmRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(transferService.verifyGuardianVerification(
                authenticatedUserId.from(authentication), transferId, verificationId, request));
    }

    @ApiOperation("거래 승인 PIN 등록 또는 변경")
    @PutMapping("/pin")
    public ResponseEntity<Void> registerOrChangePin(
            @Valid @RequestBody TransferPinRequest request, Authentication authentication) {
        transferService.registerOrChangePin(authenticatedUserId.from(authentication), request);
        return ResponseEntity.noContent().build();
    }

    @ApiOperation("송금별 PIN 인증")
    @PostMapping("/{transferId}/authenticate")
    public ResponseEntity<TransferAuthenticationResponse> authenticate(
            @PathVariable UUID transferId, @Valid @RequestBody TransferPinRequest request,
            @RequestHeader(value = "Confirmation-Token", required = false) String confirmationToken,
            Authentication authentication) {
        return ResponseEntity.ok(transferService.authenticate(
                authenticatedUserId.from(authentication), transferId, confirmationToken, request));
    }

    @ApiOperation("송금 실행")
    @PostMapping("/{transferId}/execute")
    public ResponseEntity<TransferResultResponse> execute(
            @PathVariable UUID transferId, @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "Confirmation-Token", required = false) String confirmationToken,
            Authentication authentication) {
        return ResponseEntity.ok(transferService.execute(
                authenticatedUserId.from(authentication), transferId, confirmationToken, idempotencyKey));
    }
    @ApiOperation("송금 취소")
    @DeleteMapping("/{transferId}")
    public ResponseEntity<TransferResponse> cancel(
            @PathVariable UUID transferId, Authentication authentication) {
        return ResponseEntity.ok(
                transferService.cancel(authenticatedUserId.from(authentication), transferId));
    }
}
