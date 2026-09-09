package com.silvertown.domain.reminder.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ReminderUpdateRequest {
    private String title;
    private UUID billId;
    private OffsetDateTime scheduledAt;
}
