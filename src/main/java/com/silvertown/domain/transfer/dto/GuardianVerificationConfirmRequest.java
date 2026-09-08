package com.silvertown.domain.transfer.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class GuardianVerificationConfirmRequest {
    @NotBlank(message = "보호자 인증번호는 필수입니다.")
    @Pattern(regexp = "\\d{6}", message = "보호자 인증번호는 6자리 숫자여야 합니다.")
    private String code;
}
