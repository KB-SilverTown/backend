package com.silvertown.domain.recipient.dto;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
@Getter @AllArgsConstructor
public class RecipientCandidateResponse {
    private final UUID recipientId;
    private final String displayName;
    private final String relationship;
    private final String bankCode;
    private final String accountNumberMasked;
    private final String source;
    private final OffsetDateTime lastUsedAt;
}
