package com.silvertown.domain.reminder.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ReminderListResponse {
    private final List<ReminderResponse> items;
}
