package com.silvertown.domain.recipient.vo;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter
public class Recipient {
    private String recipientId;
    private String displayName;
    private String relationship;
    private String bankCode;
    private byte[] accountNumberEncrypted;
    private String source;
    private OffsetDateTime lastUsedAt;
}
