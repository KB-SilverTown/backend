package com.silvertown.domain.reminder.service;

import com.silvertown.domain.reminder.dto.ReminderCreateRequest;
import com.silvertown.domain.reminder.dto.ReminderListResponse;
import com.silvertown.domain.reminder.dto.ReminderResponse;
import com.silvertown.domain.reminder.dto.ReminderUpdateRequest;
import java.util.UUID;

public interface ReminderService {
    ReminderListResponse find(UUID userId, String status, String from, String to);

    ReminderResponse create(UUID userId, ReminderCreateRequest request);

    ReminderResponse update(UUID userId, UUID reminderId, ReminderUpdateRequest request);

    void cancel(UUID userId, UUID reminderId);
}
