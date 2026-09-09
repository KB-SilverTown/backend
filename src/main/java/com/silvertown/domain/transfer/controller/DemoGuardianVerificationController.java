package com.silvertown.domain.transfer.controller;

import com.silvertown.domain.transfer.service.TransferService;
import com.silvertown.global.security.AuthenticatedUserId;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 테스트·로컬 데모에서만 인증번호 수신 여부를 확인한다. */
@Profile({"local", "test"})
@Api(tags = "안심 송금 데모")
@RestController
@RequestMapping(value = "/api/transfers", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class DemoGuardianVerificationController {
    private final TransferService transferService;
    private final AuthenticatedUserId authenticatedUserId;

    @ApiOperation("데모용 보호자 인증번호 수신함")
    @GetMapping("/{transferId}/guardian-verifications/{verificationId}/demo-inbox")
    public ResponseEntity<Map<String, String>> getDemoGuardianVerificationCode(
            @PathVariable UUID transferId, @PathVariable UUID verificationId,
            Authentication authentication) {
        String code = transferService.getDemoGuardianVerificationCode(
                authenticatedUserId.from(authentication), transferId, verificationId);
        return ResponseEntity.ok(Map.of("code", code));
    }
}
