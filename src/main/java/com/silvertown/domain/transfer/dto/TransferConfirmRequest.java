package com.silvertown.domain.transfer.dto;

import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TransferConfirmRequest {
    @NotNull(message = "최종 승인 여부는 필수입니다.")
    private Boolean approved;

    public static TransferConfirmRequest of(boolean approved) {
        TransferConfirmRequest request = new TransferConfirmRequest();
        request.approved = approved;
        return request;
    }
}
