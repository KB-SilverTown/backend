package com.silvertown.domain.auth.vo;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserConsentVo {

  private String consentId;
  private String userId;
  private String consentType;
  private String documentVersion;
  private boolean required;
  private boolean agreed;
  private OffsetDateTime agreedAt;
  private OffsetDateTime createdAt;
}
