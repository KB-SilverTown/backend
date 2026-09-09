package com.silvertown.domain.reminder.service.impl;

import com.silvertown.domain.reminder.dto.ReminderCreateRequest;
import com.silvertown.domain.reminder.dto.ReminderListResponse;
import com.silvertown.domain.reminder.dto.ReminderResponse;
import com.silvertown.domain.reminder.enums.ReminderStatus;
import com.silvertown.domain.reminder.mapper.ReminderMapper;
import com.silvertown.domain.reminder.service.ReminderService;
import com.silvertown.domain.reminder.vo.ReminderVo;
import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReminderServiceImpl implements ReminderService {
    private final ReminderMapper reminderMapper;
    private final Clock clock;

    @Override
    public ReminderListResponse find(UUID userId, String status, String from, String to) {
        ReminderStatus reminderStatus = parseStatus(status);
        OffsetDateTime fromAt = parseDateTime(from);
        OffsetDateTime toAt = parseDateTime(to);
        if (fromAt != null && toAt != null && fromAt.isAfter(toAt)) {
            throw new BusinessException(ErrorCode.REMINDER_QUERY_INVALID);
        }
        List<ReminderResponse> items = reminderMapper.findOwnedByCondition(
                        userId.toString(),
                        reminderStatus == null ? null : reminderStatus.name(),
                        toLocalDateTime(fromAt),
                        toLocalDateTime(toAt))
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return new ReminderListResponse(items);
    }

    @Override
    @Transactional
    public ReminderResponse create(UUID userId, ReminderCreateRequest request) {
        if (request == null || request.getTitle() == null || request.getTitle().isBlank()) {
            throw new BusinessException(ErrorCode.REMINDER_SCHEDULE_INVALID);
        }
        String title = request.getTitle().trim();
        if (title.length() > 200 || request.getScheduledAt() == null
                || !request.getScheduledAt().isAfter(OffsetDateTime.now(clock))) {
            throw new BusinessException(ErrorCode.REMINDER_SCHEDULE_INVALID);
        }

        String billId = request.getBillId() == null ? null : request.getBillId().toString();
        if (billId != null && reminderMapper.existsOwnedBill(userId.toString(), billId) == 0) {
            throw new BusinessException(ErrorCode.BILL_NOT_FOUND);
        }

        LocalDateTime remindAt = toLocalDateTime(request.getScheduledAt());
        ReminderVo reminder = new ReminderVo();
        reminder.setReminderId(UUID.randomUUID().toString());
        reminder.setUserId(userId.toString());
        reminder.setBillId(billId);
        reminder.setTitle(title);
        reminder.setRemindAt(remindAt);
        reminder.setStatus(ReminderStatus.SCHEDULED.name());
        try {
            reminderMapper.insert(reminder);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.REMINDER_DUPLICATE);
        }
        return toResponse(reminder);
    }

    private ReminderStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ReminderStatus.valueOf(status);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.REMINDER_QUERY_INVALID);
        }
    }

    private OffsetDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException exception) {
            throw new BusinessException(ErrorCode.REMINDER_QUERY_INVALID);
        }
    }

    private LocalDateTime toLocalDateTime(OffsetDateTime dateTime) {
        return dateTime == null ? null : dateTime.atZoneSameInstant(clock.getZone()).toLocalDateTime();
    }

    private ReminderResponse toResponse(ReminderVo reminder) {
        return new ReminderResponse(
                UUID.fromString(reminder.getReminderId()),
                reminder.getTitle(),
                reminder.getBillId() == null ? null : UUID.fromString(reminder.getBillId()),
                reminder.getRemindAt().atZone(clock.getZone()).toOffsetDateTime(),
                ReminderStatus.valueOf(reminder.getStatus()));
    }
}
