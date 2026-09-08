package com.silvertown.domain.transfer.dto;

import java.util.UUID;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TransferPrepareRequest {
    @NotNull(message = "출금 계좌는 필수입니다.")
    private UUID fromAccountId;
    @NotNull(message = "수취인은 필수입니다.")
    private UUID recipientId;
    private Long amount;
    private UUID voiceSessionId;

    public static TransferPrepareRequest of(
            UUID fromAccountId, UUID recipientId, Long amount, UUID voiceSessionId) {
        TransferPrepareRequest request = new TransferPrepareRequest();
        request.fromAccountId = fromAccountId;
        request.recipientId = recipientId;
        request.amount = amount;
        request.voiceSessionId = voiceSessionId;
        return request;
    }
}
