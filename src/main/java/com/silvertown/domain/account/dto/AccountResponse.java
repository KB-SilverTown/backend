package com.silvertown.domain.account.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AccountResponse {
    private final UUID accountId;
    private final String bankCode;
    private final String accountNumberMasked;
    private final String accountName;
    private final long balance;
    private final String accountType;
    private final boolean active;
    private final OffsetDateTime syncedAt;
}
