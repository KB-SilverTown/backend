package com.silvertown.domain.transfer.vo;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TransferConfirmation {
    private String confirmationId;
    private String transferId;
    private String userId;
    private String confirmationType;
    private boolean approved;
    private String confirmationText;
    private OffsetDateTime confirmedAt;
}
