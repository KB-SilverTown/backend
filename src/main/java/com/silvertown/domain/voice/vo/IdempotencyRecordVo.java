package com.silvertown.domain.voice.vo;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class IdempotencyRecordVo {
    private String idempotencyRecordId;
    private String userId;
    private String operation;
    private String idempotencyKey;
    private String requestHash;
    private String status;
    private String resourceType;
    private String resourceId;
    private Integer responseStatus;
    private String responseBody;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
