package com.silvertown.domain.auth.dto;

import java.util.Collections;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SignUpRequest {

  @NotBlank(message = "아이디는 필수입니다.")
  @Size(max = 100, message = "아이디는 100자 이하여야 합니다.")
  private String loginId;

  @NotBlank(message = "비밀번호는 필수입니다.")
  @Size(min = 8, max = 100, message = "비밀번호는 8자 이상 100자 이하여야 합니다.")
  private String password;

  @NotBlank(message = "이름은 필수입니다.")
  @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
  private String name;

  @NotBlank(message = "주민등록번호는 필수입니다.")
  @Size(max = 100, message = "주민등록번호 형식이 올바르지 않습니다.")
  @Pattern(regexp = "\\d{6}-?[1-8]\\d{6}", message = "주민등록번호 형식이 올바르지 않습니다.")
  private String residentRegistrationNumber;

  @NotBlank(message = "성별은 필수입니다.")
  @Pattern(regexp = "MALE|FEMALE", message = "성별은 MALE 또는 FEMALE이어야 합니다.")
  private String gender;

  @NotBlank(message = "우편번호는 필수입니다.")
  @Size(max = 20, message = "우편번호 형식이 올바르지 않습니다.")
  private String postalCode;

  @NotBlank(message = "주소는 필수입니다.")
  @Size(max = 255, message = "주소는 255자 이하여야 합니다.")
  private String address;

  @Size(max = 255, message = "상세주소는 255자 이하여야 합니다.")
  private String detailAddress;

  @NotBlank(message = "은행 코드는 필수입니다.")
  @Size(max = 20, message = "은행 코드 형식이 올바르지 않습니다.")
  private String bankCode;

  @NotBlank(message = "계좌번호는 필수입니다.")
  @Size(max = 100, message = "계좌번호 형식이 올바르지 않습니다.")
  private String accountNumber;

  @NotBlank(message = "전화번호는 필수입니다.")
  @Size(max = 30, message = "전화번호 형식이 올바르지 않습니다.")
  private String phone;

  @Valid
  @NotNull(message = "비상 연락처는 필수입니다.")
  private EmergencyContactRequest emergencyContact;

  @Valid
  @NotNull(message = "동의 정보는 필수입니다.")
  private List<ConsentAgreementRequest> consents = Collections.emptyList();

  @Getter
  @NoArgsConstructor
  public static class EmergencyContactRequest {

    @NotBlank(message = "비상 연락처 이름은 필수입니다.")
    @Size(max = 50, message = "비상 연락처 이름은 50자 이하여야 합니다.")
    private String name;

    @NotBlank(message = "비상 연락처 전화번호는 필수입니다.")
    @Size(max = 30, message = "비상 연락처 전화번호 형식이 올바르지 않습니다.")
    private String phone;

    @NotBlank(message = "비상 연락처 관계는 필수입니다.")
    @Size(max = 50, message = "비상 연락처 관계는 50자 이하여야 합니다.")
    private String relationship;
  }

  @Getter
  @NoArgsConstructor
  public static class ConsentAgreementRequest {

    @NotBlank(message = "동의 유형은 필수입니다.")
    @Size(max = 50, message = "동의 유형 형식이 올바르지 않습니다.")
    private String type;

    private boolean agreed;

    @NotBlank(message = "동의 문서 버전은 필수입니다.")
    @Size(max = 50, message = "동의 문서 버전 형식이 올바르지 않습니다.")
    private String documentVersion;
  }
}
