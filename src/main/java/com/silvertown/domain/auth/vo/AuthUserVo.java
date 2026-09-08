package com.silvertown.domain.auth.vo;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuthUserVo {

  private String userId;
  private String loginId;
  private String passwordHash;
  private String status;
  private OffsetDateTime lastLoginAt;
  private OffsetDateTime createdAt;
}
