package com.silvertown.domain.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvertown.domain.account.mapper.AccountMapper;
import com.silvertown.domain.auth.dto.LoginRequest;
import com.silvertown.domain.auth.dto.SignUpRequest;
import com.silvertown.domain.auth.mapper.AuthMapper;
import com.silvertown.domain.auth.service.impl.AuthServiceImpl;
import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import com.silvertown.global.security.JwtTokenProvider;
import com.silvertown.global.security.SensitiveDataHasher;
import com.silvertown.global.security.crypto.AccountNumberCrypto;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceImplTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private AuthMapper authMapper;
  private AccountMapper accountMapper;
  private AuthLoginAttemptService loginAttemptService;
  private AuthService authService;

  @BeforeEach
  void setUp() {
    authMapper = mock(AuthMapper.class);
    accountMapper = mock(AccountMapper.class);
    loginAttemptService = mock(AuthLoginAttemptService.class);
    authService = new AuthServiceImpl(
        authMapper,
        accountMapper,
        mock(AccountNumberCrypto.class),
        mock(SensitiveDataHasher.class),
        mock(JwtTokenProvider.class),
        loginAttemptService,
        mock(PasswordEncoder.class),
        Clock.fixed(Instant.parse("2026-09-05T00:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void rejectsANullSignUpRequestBeforeAccessingPersistence() {
    BusinessException exception = assertThrows(
        BusinessException.class, () -> authService.signUp(null));

    assertEquals(ErrorCode.SIGNUP_REQUEST_INVALID, exception.getErrorCode());
    verifyNoInteractions(authMapper, accountMapper);
  }

  @Test
  void rejectsAMissingConsentCollectionBeforeAccessingPersistence() throws Exception {
    SignUpRequest request = objectMapper.readValue("{\"consents\":null}", SignUpRequest.class);

    BusinessException exception = assertThrows(
        BusinessException.class, () -> authService.signUp(request));

    assertEquals(ErrorCode.SIGNUP_REQUEST_INVALID, exception.getErrorCode());
    verifyNoInteractions(authMapper, accountMapper);
  }

  @Test
  void rejectsANullLoginRequestBeforeAccessingTheLoginAttemptStore() {
    BusinessException exception = assertThrows(
        BusinessException.class, () -> authService.login((LoginRequest) null));

    assertEquals(ErrorCode.AUTH_REQUEST_INVALID, exception.getErrorCode());
    verifyNoInteractions(loginAttemptService, authMapper);
  }
}
