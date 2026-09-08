package com.silvertown.domain.reminder.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.silvertown.domain.reminder.dto.ReminderCreateRequest;
import com.silvertown.domain.reminder.dto.ReminderListResponse;
import com.silvertown.domain.reminder.dto.ReminderResponse;
import com.silvertown.domain.reminder.enums.ReminderStatus;
import com.silvertown.domain.reminder.mapper.ReminderMapper;
import com.silvertown.domain.reminder.service.impl.ReminderServiceImpl;
import com.silvertown.domain.reminder.vo.ReminderVo;
import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class ReminderServiceImplTest {
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BILL_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-02T01:00:00Z"), ZoneId.of("Asia/Seoul"));

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private ReminderMapper reminderMapper;
    private ReminderService reminderService;

    @BeforeEach
    void setUp() {
        reminderMapper = Mockito.mock(ReminderMapper.class);
        reminderService = new ReminderServiceImpl(reminderMapper, CLOCK);
    }

    @Test
    void createsScheduledReminderAfterValidatingOwnedBill() throws Exception {
        when(reminderMapper.existsOwnedBill(USER_ID.toString(), BILL_ID.toString())).thenReturn(1);

        ReminderResponse response = reminderService.create(USER_ID, request(
                "{\"title\":\" 전기요금 납부 \",\"billId\":\"" + BILL_ID
                        + "\",\"scheduledAt\":\"2026-09-09T09:00:00+09:00\"}"));

        ArgumentCaptor<ReminderVo> captor = ArgumentCaptor.forClass(ReminderVo.class);
        verify(reminderMapper).insert(captor.capture());
        ReminderVo saved = captor.getValue();
        assertEquals(USER_ID.toString(), saved.getUserId());
        assertEquals(BILL_ID.toString(), saved.getBillId());
        assertEquals("전기요금 납부", saved.getTitle());
        assertEquals(LocalDateTime.of(2026, 9, 9, 9, 0), saved.getRemindAt());
        assertEquals("SCHEDULED", saved.getStatus());
        assertEquals("전기요금 납부", response.getTitle());
        assertEquals(ReminderStatus.SCHEDULED, response.getStatus());
    }

    @Test
    void rejectsPastScheduleBeforeAnyPersistenceCall() throws Exception {
        BusinessException exception = assertThrows(BusinessException.class, () -> reminderService.create(
                USER_ID,
                request("{\"title\":\"전기요금 납부\",\"scheduledAt\":\"2026-09-02T09:00:00+09:00\"}")));

        assertEquals(ErrorCode.REMINDER_SCHEDULE_INVALID, exception.getErrorCode());
        verify(reminderMapper, never()).insert(any());
    }

    @Test
    void rejectsBillOutsideAuthenticatedUsersOwnership() throws Exception {
        when(reminderMapper.existsOwnedBill(USER_ID.toString(), BILL_ID.toString())).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class, () -> reminderService.create(
                USER_ID,
                request("{\"title\":\"전기요금 납부\",\"billId\":\"" + BILL_ID
                        + "\",\"scheduledAt\":\"2026-09-09T09:00:00+09:00\"}")));

        assertEquals(ErrorCode.BILL_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void rejectsDuplicateReminder() throws Exception {
        doThrow(new DuplicateKeyException("duplicate reminder"))
                .when(reminderMapper)
                .insert(any(ReminderVo.class));

        BusinessException exception = assertThrows(BusinessException.class, () -> reminderService.create(
                USER_ID,
                request("{\"title\":\"전기요금 납부\",\"scheduledAt\":\"2026-09-09T09:00:00+09:00\"}")));

        assertEquals(ErrorCode.REMINDER_DUPLICATE, exception.getErrorCode());
    }

    @Test
    void findsOwnedRemindersWithValidatedFilters() {
        ReminderVo reminder = new ReminderVo();
        reminder.setReminderId("20000000-0000-0000-0000-000000000001");
        reminder.setTitle("전기요금 납부");
        reminder.setRemindAt(LocalDateTime.of(2026, 9, 9, 9, 0));
        reminder.setStatus("SCHEDULED");
        when(reminderMapper.findOwnedByCondition(
                USER_ID.toString(), "SCHEDULED", LocalDateTime.of(2026, 9, 1, 0, 0),
                LocalDateTime.of(2026, 9, 30, 23, 59))).thenReturn(List.of(reminder));

        ReminderListResponse response = reminderService.find(
                USER_ID,
                "SCHEDULED",
                "2026-09-01T00:00:00+09:00",
                "2026-09-30T23:59:00+09:00");

        assertEquals(1, response.getItems().size());
        assertEquals(
                OffsetDateTime.parse("2026-09-09T09:00:00+09:00"),
                response.getItems().get(0).getScheduledAt());
    }

    @Test
    void rejectsInvalidQueryCondition() {
        BusinessException exception = assertThrows(BusinessException.class, () -> reminderService.find(
                USER_ID, "SCHEDULED", "2026-09-10T00:00:00+09:00", "2026-09-01T00:00:00+09:00"));

        assertEquals(ErrorCode.REMINDER_QUERY_INVALID, exception.getErrorCode());
    }

    private ReminderCreateRequest request(String json) throws Exception {
        return objectMapper.readValue(json, ReminderCreateRequest.class);
    }
}
