package com.silvertown.domain.auth.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class LoginRequest {

  @NotBlank(message = "아이디는 필수입니다.")
  @Size(max = 100, message = "아이디는 100자 이하여야 합니다.")
  private String loginId;

  @NotBlank(message = "비밀번호는 필수입니다.")
  @Size(max = 100, message = "비밀번호는 100자 이하여야 합니다.")
  private String password;
}
