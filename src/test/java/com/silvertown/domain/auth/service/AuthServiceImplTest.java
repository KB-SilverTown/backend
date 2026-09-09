package com.silvertown.domain.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvertown.domain.account.mapper.AccountMapper;
import com.silvertown.domain.auth.dto.LoginRequest;
import com.silvertown.domain.auth.dto.SignUpRequest;
import com.silvertown.domain.auth.dto.UserProfileResponse;
import com.silvertown.domain.auth.mapper.AuthMapper;
import com.silvertown.domain.auth.service.impl.AuthServiceImpl;
import com.silvertown.domain.auth.vo.CurrentUserProfileVo;
import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import com.silvertown.global.security.JwtTokenProvider;
import com.silvertown.global.security.SensitiveDataHasher;
import com.silvertown.global.security.crypto.AccountNumberCrypto;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceImplTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private AuthMapper authMapper;
  private AccountMapper accountMapper;
  private AuthLoginAttemptService loginAttemptService;
  private AccountNumberCrypto sensitiveDataCrypto;
  private AuthService authService;

  @BeforeEach
  void setUp() {
    authMapper = mock(AuthMapper.class);
    accountMapper = mock(AccountMapper.class);
    loginAttemptService = mock(AuthLoginAttemptService.class);
    sensitiveDataCrypto = mock(AccountNumberCrypto.class);
    authService = new AuthServiceImpl(
        authMapper,
        accountMapper,
        sensitiveDataCrypto,
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

  @Test
  void returnsOnlyCurrentUsersProfileFields() {
    UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    CurrentUserProfileVo profile = new CurrentUserProfileVo();
    profile.setUserId(userId.toString());
    profile.setLoginId("senior01");
    profile.setName("홍길동");
    profile.setPhoneEncrypted(new byte[] {1, 2, 3});
    profile.setPostalCode("06234");
    profile.setAddress("서울특별시 강남구 테헤란로 1");
    profile.setDetailAddress("101호");
    when(authMapper.findCurrentUserProfileByUserId(userId.toString())).thenReturn(profile);
    when(sensitiveDataCrypto.decrypt(profile.getPhoneEncrypted())).thenReturn("01012345678");

    UserProfileResponse response = authService.getCurrentUserProfile(userId);

    assertEquals(userId, response.getUserId());
    assertEquals("senior01", response.getLoginId());
    assertEquals("홍길동", response.getName());
    assertEquals("010-****-5678", response.getPhoneMasked());
    assertEquals("06234", response.getPostalCode());
    assertEquals("서울특별시 강남구 테헤란로 1", response.getAddress());
    assertEquals("101호", response.getDetailAddress());
  }

  @Test
  void rejectsAProfileLookupForAnUnknownAuthenticatedUser() {
    UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");

    BusinessException exception = assertThrows(
        BusinessException.class, () -> authService.getCurrentUserProfile(userId));

    assertEquals(ErrorCode.INVALID_AUTHENTICATED_USER, exception.getErrorCode());
  }

  @Test
  void returnsProfileWithoutMaskedPhoneWhenStoredPhoneCannotBeDecrypted() {
    UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    CurrentUserProfileVo profile = new CurrentUserProfileVo();
    profile.setUserId(userId.toString());
    profile.setLoginId("senior01");
    profile.setName("홍길동");
    profile.setPhoneEncrypted(new byte[] {1, 2, 3});
    profile.setPostalCode("06234");
    profile.setAddress("서울특별시 강남구 테헤란로 1");
    when(authMapper.findCurrentUserProfileByUserId(userId.toString())).thenReturn(profile);
    when(sensitiveDataCrypto.decrypt(profile.getPhoneEncrypted()))
        .thenThrow(new BusinessException(ErrorCode.ACCOUNT_CRYPTO_FAILURE));

    UserProfileResponse response = authService.getCurrentUserProfile(userId);

    assertNull(response.getPhoneMasked());
    assertEquals("홍길동", response.getName());
  }
}
