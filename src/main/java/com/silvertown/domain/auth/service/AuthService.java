package com.silvertown.domain.auth.service;

import com.silvertown.domain.auth.dto.AuthResponse;
import com.silvertown.domain.auth.dto.LoginRequest;
import com.silvertown.domain.auth.dto.SignUpRequest;
import com.silvertown.domain.auth.dto.UserProfileResponse;
import java.util.UUID;

public interface AuthService {

  AuthResponse signUp(SignUpRequest request);

  AuthResponse login(LoginRequest request);

  AuthResponse refresh(String refreshToken);

  void logout(String refreshToken);

  UserProfileResponse getCurrentUserProfile(UUID userId);
}
