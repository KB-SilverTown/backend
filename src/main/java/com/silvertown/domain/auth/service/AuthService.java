package com.silvertown.domain.auth.service;

import com.silvertown.domain.auth.dto.AuthResponse;
import com.silvertown.domain.auth.dto.LoginRequest;
import com.silvertown.domain.auth.dto.SignUpRequest;

public interface AuthService {

  AuthResponse signUp(SignUpRequest request);

  AuthResponse login(LoginRequest request);

  AuthResponse refresh(String refreshToken);

  void logout(String refreshToken);
}
