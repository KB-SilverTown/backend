package com.silvertown.domain.auth.vo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CurrentUserProfileVo {

  private String userId;
  private String loginId;
  private String name;
  private byte[] phoneEncrypted;
  private String postalCode;
  private String address;
  private String detailAddress;
}
