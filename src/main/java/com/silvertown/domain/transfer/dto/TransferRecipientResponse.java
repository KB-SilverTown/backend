package com.silvertown.domain.transfer.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TransferRecipientResponse {
    private final UUID recipientId;
    private final String displayName;
    private final String bankCode;
    private final String accountNumberMasked;
}
