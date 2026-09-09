package com.silvertown.domain.auth.controller;

import com.silvertown.domain.auth.dto.UserProfileResponse;
import com.silvertown.domain.auth.service.AuthService;
import com.silvertown.global.security.AuthenticatedUserId;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Api(tags = "인증")
@RestController
@RequestMapping(value = "/api/users/me", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class UserProfileController {

  private final AuthService authService;
  private final AuthenticatedUserId authenticatedUserId;

  @ApiOperation("내 사용자 정보 조회")
  @GetMapping
  public ResponseEntity<UserProfileResponse> getCurrentUserProfile(Authentication authentication) {
    return ResponseEntity.ok(authService.getCurrentUserProfile(authenticatedUserId.from(authentication)));
  }
}
