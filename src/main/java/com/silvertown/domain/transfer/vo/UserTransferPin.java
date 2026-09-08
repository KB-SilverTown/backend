package com.silvertown.domain.transfer.vo;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserTransferPin {
    private String userId;
    private String pinHash;
    private int failedAttemptCount;
    private OffsetDateTime lockedUntil;
}
