package com.silvertown.domain.reminder.dto;

import com.silvertown.domain.reminder.enums.ReminderStatus;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ReminderResponse {
    private final UUID id;
    private final String title;
    private final UUID billId;
    private final OffsetDateTime scheduledAt;
    private final ReminderStatus status;
}
