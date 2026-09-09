package com.silvertown.domain.auth.service.impl;

import com.silvertown.domain.account.mapper.AccountMapper;
import com.silvertown.domain.auth.dto.AuthResponse;
import com.silvertown.domain.auth.dto.LoginRequest;
import com.silvertown.domain.auth.dto.SignUpRequest;
import com.silvertown.domain.auth.dto.UserProfileResponse;
import com.silvertown.domain.auth.mapper.AuthMapper;
import com.silvertown.domain.auth.service.AuthLoginAttemptService;
import com.silvertown.domain.auth.service.AuthService;
import com.silvertown.domain.auth.vo.AuthUserVo;
import com.silvertown.domain.auth.vo.CurrentUserProfileVo;
import com.silvertown.domain.auth.vo.RefreshTokenVo;
import com.silvertown.domain.auth.vo.UserConsentVo;
import com.silvertown.domain.auth.vo.UserProfileVo;
import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import com.silvertown.global.security.JwtTokenProvider;
import com.silvertown.global.security.SensitiveDataHasher;
import com.silvertown.global.security.crypto.AccountNumberCrypto;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

  private static final String ACTIVE_STATUS = "ACTIVE";
  private static final Set<String> REQUIRED_CONSENT_TYPES = Set.of(
      "TERMS_OF_SERVICE",
      "PRIVACY_COLLECTION",
      "MYDATA_FINANCIAL",
      "AI_VOICE_DATA");
  private static final Set<String> ALLOWED_CONSENT_TYPES = Set.of(
      "TERMS_OF_SERVICE",
      "PRIVACY_COLLECTION",
      "MYDATA_FINANCIAL",
      "AI_VOICE_DATA",
      "AI_FINANCIAL_DATA_OPTIONAL");

  private final AuthMapper authMapper;
  private final AccountMapper accountMapper;
  private final AccountNumberCrypto sensitiveDataCrypto;
  private final SensitiveDataHasher hasher;
  private final JwtTokenProvider jwtTokenProvider;
  private final AuthLoginAttemptService loginAttemptService;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;

  @Override
  @Transactional
  public AuthResponse signUp(SignUpRequest request) {
    if (request == null) {
      throw new BusinessException(ErrorCode.SIGNUP_REQUEST_INVALID);
    }
    validateConsents(request.getConsents());
    if (authMapper.findUserByLoginId(request.getLoginId()) != null) {
      throw new BusinessException(ErrorCode.LOGIN_ID_DUPLICATE);
    }

    UUID userId = UUID.randomUUID();
    OffsetDateTime now = OffsetDateTime.now(clock);
    try {
      AuthUserVo user = new AuthUserVo();
      user.setUserId(userId.toString());
      user.setLoginId(request.getLoginId());
      user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
      user.setStatus(ACTIVE_STATUS);
      user.setCreatedAt(now);
      authMapper.insertUser(user);

      authMapper.insertUserProfile(toProfile(userId, request, now));
      accountMapper.insertPrimaryAccount(
          UUID.randomUUID().toString(),
          userId.toString(),
          request.getBankCode(),
          sensitiveDataCrypto.encrypt(request.getAccountNumber()),
          hasher.hashNumericValue(request.getAccountNumber()),
          now);
      insertConsents(userId, request.getConsents(), now);

      return issueAndPersistTokens(userId, now);
    } catch (DuplicateKeyException exception) {
      throw new BusinessException(ErrorCode.SIGNUP_REQUEST_INVALID);
    }
  }

  @Override
  @Transactional
  public AuthResponse login(LoginRequest request) {
    if (request == null) {
      throw new BusinessException(ErrorCode.AUTH_REQUEST_INVALID);
    }
    return loginAttemptService.executeWithLoginLock(
        request.getLoginId(), () -> loginWithinLock(request));
  }

  private AuthResponse loginWithinLock(LoginRequest request) {
    if (loginAttemptService.isBlocked(request.getLoginId())) {
      throw new BusinessException(ErrorCode.AUTH_TOO_MANY_ATTEMPTS);
    }

    AuthUserVo user = authMapper.findUserByLoginId(request.getLoginId());
    if (user == null
        || !ACTIVE_STATUS.equals(user.getStatus())
        || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
      loginAttemptService.recordFailure(request.getLoginId());
      throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }

    loginAttemptService.clear(request.getLoginId());
    UUID userId = UUID.fromString(user.getUserId());
    OffsetDateTime now = OffsetDateTime.now(clock);
    authMapper.updateLastLoginAt(user.getUserId(), now);
    return issueAndPersistTokens(userId, now);
  }

  @Override
  @Transactional
  public AuthResponse refresh(String refreshToken) {
    requireRefreshToken(refreshToken);
    UUID userId = jwtTokenProvider.getRefreshTokenUserId(refreshToken);
    RefreshTokenVo current = authMapper.findRefreshTokenByHash(hasher.hash(refreshToken));
    if (current == null || !userId.toString().equals(current.getUserId())) {
      throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
    }

    OffsetDateTime now = OffsetDateTime.now(clock);
    if (current.getExpiresAt().isBefore(now)) {
      throw new BusinessException(ErrorCode.REFRESH_TOKEN_EXPIRED);
    }
    if (current.getRevokedAt() != null) {
      throw new BusinessException(ErrorCode.REFRESH_TOKEN_REUSED);
    }

    JwtTokenProvider.IssuedTokenPair issued = jwtTokenProvider.issue(userId);
    String nextRefreshTokenId = UUID.randomUUID().toString();
    insertRefreshToken(nextRefreshTokenId, userId, issued, now);
    int updated = authMapper.revokeRefreshTokenForRotation(
        current.getRefreshTokenId(), nextRefreshTokenId, now);
    if (updated != 1) {
      throw new BusinessException(ErrorCode.REFRESH_TOKEN_REUSED);
    }
    return toResponse(userId, issued);
  }

  @Override
  @Transactional
  public void logout(String refreshToken) {
    requireRefreshToken(refreshToken);
    authMapper.revokeRefreshTokenForLogout(hasher.hash(refreshToken), OffsetDateTime.now(clock));
  }

  @Override
  @Transactional(readOnly = true)
  public UserProfileResponse getCurrentUserProfile(UUID userId) {
    CurrentUserProfileVo profile = authMapper.findCurrentUserProfileByUserId(userId.toString());
    if (profile == null) {
      throw new BusinessException(ErrorCode.INVALID_AUTHENTICATED_USER);
    }

    return new UserProfileResponse(
        userId,
        profile.getLoginId(),
        profile.getName(),
        maskedPhoneNumberOrNull(profile.getPhoneEncrypted(), userId),
        profile.getPostalCode(),
        profile.getAddress(),
        profile.getDetailAddress());
  }

  private String maskedPhoneNumberOrNull(byte[] encryptedPhone, UUID userId) {
    try {
      return maskPhoneNumber(sensitiveDataCrypto.decrypt(encryptedPhone));
    } catch (BusinessException exception) {
      if (exception.getErrorCode() != ErrorCode.ACCOUNT_CRYPTO_FAILURE
          && exception.getErrorCode() != ErrorCode.ACCOUNT_CRYPTO_NOT_CONFIGURED) {
        throw exception;
      }
      log.warn("Current user profile phone could not be decrypted. userId={}, errorCode={}",
          userId, exception.getErrorCode().getCode());
      return null;
    }
  }

  private UserProfileVo toProfile(UUID userId, SignUpRequest request, OffsetDateTime now) {
    UserProfileVo profile = new UserProfileVo();
    profile.setUserId(userId.toString());
    profile.setName(request.getName());
    profile.setResidentNumberEncrypted(sensitiveDataCrypto.encrypt(request.getResidentRegistrationNumber()));
    profile.setResidentNumberHash(hasher.hashNumericValue(request.getResidentRegistrationNumber()));
    profile.setGender(request.getGender());
    profile.setPostalCode(request.getPostalCode());
    profile.setAddress(request.getAddress());
    profile.setDetailAddress(request.getDetailAddress());
    profile.setPhoneEncrypted(sensitiveDataCrypto.encrypt(request.getPhone()));
    profile.setPhoneHash(hasher.hashNumericValue(request.getPhone()));
    profile.setEmergencyContactPhoneEncrypted(
        sensitiveDataCrypto.encrypt(request.getEmergencyContact().getPhone()));
    profile.setEmergencyContactPhoneHash(
        hasher.hashNumericValue(request.getEmergencyContact().getPhone()));
    profile.setEmergencyContactName(request.getEmergencyContact().getName());
    profile.setEmergencyContactRelationship(request.getEmergencyContact().getRelationship());
    profile.setCreatedAt(now);
    return profile;
  }

  private void insertConsents(
      UUID userId, List<SignUpRequest.ConsentAgreementRequest> consents, OffsetDateTime now) {
    for (SignUpRequest.ConsentAgreementRequest agreement : consents) {
      UserConsentVo consent = new UserConsentVo();
      consent.setConsentId(UUID.randomUUID().toString());
      consent.setUserId(userId.toString());
      consent.setConsentType(agreement.getType());
      consent.setDocumentVersion(agreement.getDocumentVersion());
      consent.setRequired(REQUIRED_CONSENT_TYPES.contains(agreement.getType()));
      consent.setAgreed(agreement.isAgreed());
      consent.setAgreedAt(agreement.isAgreed() ? now : null);
      consent.setCreatedAt(now);
      authMapper.insertUserConsent(consent);
    }
  }

  private AuthResponse issueAndPersistTokens(UUID userId, OffsetDateTime now) {
    JwtTokenProvider.IssuedTokenPair issued = jwtTokenProvider.issue(userId);
    insertRefreshToken(UUID.randomUUID().toString(), userId, issued, now);
    return toResponse(userId, issued);
  }

  private void insertRefreshToken(
      String refreshTokenId,
      UUID userId,
      JwtTokenProvider.IssuedTokenPair issued,
      OffsetDateTime now) {
    RefreshTokenVo refreshToken = new RefreshTokenVo();
    refreshToken.setRefreshTokenId(refreshTokenId);
    refreshToken.setUserId(userId.toString());
    refreshToken.setTokenHash(hasher.hash(issued.getRefreshToken()));
    refreshToken.setExpiresAt(issued.getRefreshExpiresAt());
    refreshToken.setCreatedAt(now);
    authMapper.insertRefreshToken(refreshToken);
  }

  private AuthResponse toResponse(UUID userId, JwtTokenProvider.IssuedTokenPair issued) {
    return new AuthResponse(
        issued.getAccessToken(), issued.getRefreshToken(), issued.getAccessExpiresAt(), userId);
  }

  private void validateConsents(List<SignUpRequest.ConsentAgreementRequest> consents) {
    if (consents == null) {
      throw new BusinessException(ErrorCode.SIGNUP_REQUEST_INVALID);
    }
    Set<String> seenTypes = new HashSet<>();
    for (SignUpRequest.ConsentAgreementRequest agreement : consents) {
      if (!ALLOWED_CONSENT_TYPES.contains(agreement.getType()) || !seenTypes.add(agreement.getType())) {
        throw new BusinessException(ErrorCode.SIGNUP_REQUEST_INVALID);
      }
      if (REQUIRED_CONSENT_TYPES.contains(agreement.getType()) && !agreement.isAgreed()) {
        throw new BusinessException(ErrorCode.REQUIRED_CONSENT_MISSING);
      }
    }
    if (!seenTypes.containsAll(REQUIRED_CONSENT_TYPES)) {
      throw new BusinessException(ErrorCode.REQUIRED_CONSENT_MISSING);
    }
  }

  private void requireRefreshToken(String refreshToken) {
    if (refreshToken == null || refreshToken.isBlank()) {
      throw new BusinessException(ErrorCode.REFRESH_TOKEN_MISSING);
    }
  }

  private String maskPhoneNumber(String phone) {
    String digits = phone.replaceAll("\\D", "");
    if (!digits.matches("01[016789]\\d{7,8}")) {
      return "****";
    }
    return digits.substring(0, 3) + "-****-" + digits.substring(digits.length() - 4);
  }
}
