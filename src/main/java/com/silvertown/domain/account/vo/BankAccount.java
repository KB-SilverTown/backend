package com.silvertown.domain.account.vo;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter
public class BankAccount {
    private String accountId;
    private String bankCode;
    private byte[] accountNumberEncrypted;
    private String accountName;
    private long balance;
    private String accountType;
    private boolean active;
    private OffsetDateTime syncedAt;
}
