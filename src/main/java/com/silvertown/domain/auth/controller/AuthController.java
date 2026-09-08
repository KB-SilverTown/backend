package com.silvertown.domain.auth.controller;

import com.silvertown.domain.auth.dto.AuthResponse;
import com.silvertown.domain.auth.dto.LoginRequest;
import com.silvertown.domain.auth.dto.LogoutRequest;
import com.silvertown.domain.auth.dto.SignUpRequest;
import com.silvertown.domain.auth.dto.TokenRefreshRequest;
import com.silvertown.domain.auth.service.AuthService;
import com.silvertown.global.common.exception.ApiErrorResponse;
import com.silvertown.global.common.exception.ErrorCode;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.stream.Collectors;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Api(tags = "인증")
@RestController
@RequestMapping(value = "/api/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @ApiOperation("회원가입")
  @PostMapping("/signup")
  public ResponseEntity<AuthResponse> signUp(@Valid @RequestBody SignUpRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(authService.signUp(request));
  }

  @ApiOperation("로그인")
  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
    return ResponseEntity.ok(authService.login(request));
  }

  @ApiOperation("액세스 토큰 갱신")
  @PostMapping("/refresh")
  public ResponseEntity<AuthResponse> refresh(@RequestBody TokenRefreshRequest request) {
    return ResponseEntity.ok(authService.refresh(request == null ? null : request.getRefreshToken()));
  }

  @ApiOperation("로그아웃")
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(@RequestBody LogoutRequest request) {
    authService.logout(request == null ? null : request.getRefreshToken());
    return ResponseEntity.noContent().build();
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiErrorResponse> handleInvalidRequest(MethodArgumentNotValidException exception) {
    ErrorCode errorCode = exception.getBindingResult().getTarget() instanceof LoginRequest
        ? ErrorCode.AUTH_REQUEST_INVALID
        : ErrorCode.SIGNUP_REQUEST_INVALID;
    return ResponseEntity.status(errorCode.getStatus()).body(ApiErrorResponse.of(
        errorCode,
        null,
        exception.getBindingResult().getFieldErrors().stream()
            .map(error -> new ApiErrorResponse.FieldErrorDetail(error.getField(), error.getDefaultMessage()))
            .collect(Collectors.toList())));
  }
}
