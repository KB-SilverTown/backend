package com.silvertown.domain.account.controller;
import com.silvertown.domain.account.dto.AccountResponse;
import com.silvertown.domain.account.service.AccountService;
import com.silvertown.global.security.AuthenticatedUserId;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@Api(tags = "계좌") @RestController @RequestMapping("/api/accounts") @RequiredArgsConstructor
public class AccountController {
    private final AccountService accountService;
    private final AuthenticatedUserId authenticatedUserId;
    @ApiOperation("내 활성 계좌 목록 조회") @GetMapping
    public ResponseEntity<List<AccountResponse>> getAccounts(Authentication authentication) {
        return ResponseEntity.ok(accountService.getAccounts(authenticatedUserId.from(authentication)));
    }
}
