package com.silvertown.domain.auth.vo;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserProfileVo {

  private String userId;
  private String name;
  private byte[] residentNumberEncrypted;
  private String residentNumberHash;
  private String gender;
  private String postalCode;
  private String address;
  private String detailAddress;
  private byte[] phoneEncrypted;
  private String phoneHash;
  private byte[] emergencyContactPhoneEncrypted;
  private String emergencyContactPhoneHash;
  private String emergencyContactName;
  private String emergencyContactRelationship;
  private OffsetDateTime createdAt;
}
