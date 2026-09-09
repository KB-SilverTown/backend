package com.silvertown.domain.auth.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserProfileResponse {

  private final UUID userId;
  private final String loginId;
  private final String name;
  private final String phone;
  private final String postalCode;
  private final String address;
  private final String detailAddress;
}
