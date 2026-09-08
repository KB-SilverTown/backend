package com.silvertown.domain.transfer.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TransferPinRequest {
    @NotBlank(message = "거래 승인 PIN은 필수입니다.")
    @Pattern(regexp = "\\d{6}", message = "거래 승인 PIN은 숫자 6자리여야 합니다.")
    private String pin;
}
