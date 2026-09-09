package com.silvertown.domain.reminder.vo;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ReminderVo {
    private String reminderId;
    private String userId;
    private String billId;
    private String title;
    private LocalDateTime remindAt;
    private String status;
}
