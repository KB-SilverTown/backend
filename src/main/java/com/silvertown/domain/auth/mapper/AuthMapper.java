package com.silvertown.domain.auth.mapper;

import com.silvertown.domain.auth.vo.AuthUserVo;
import com.silvertown.domain.auth.vo.RefreshTokenVo;
import com.silvertown.domain.auth.vo.UserConsentVo;
import com.silvertown.domain.auth.vo.UserProfileVo;
import java.time.OffsetDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuthMapper {

  AuthUserVo findUserByLoginId(@Param("loginId") String loginId);

  int insertUser(AuthUserVo user);

  int insertUserProfile(UserProfileVo profile);

  int insertUserConsent(UserConsentVo consent);

  int updateLastLoginAt(@Param("userId") String userId, @Param("lastLoginAt") OffsetDateTime lastLoginAt);

  int insertRefreshToken(RefreshTokenVo refreshToken);

  RefreshTokenVo findRefreshTokenByHash(@Param("tokenHash") String tokenHash);

  int revokeRefreshTokenForRotation(
      @Param("refreshTokenId") String refreshTokenId,
      @Param("replacedByTokenId") String replacedByTokenId,
      @Param("revokedAt") OffsetDateTime revokedAt);

  int revokeRefreshTokenForLogout(
      @Param("tokenHash") String tokenHash, @Param("revokedAt") OffsetDateTime revokedAt);
}
