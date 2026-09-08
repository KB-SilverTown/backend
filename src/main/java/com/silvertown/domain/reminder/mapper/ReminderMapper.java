package com.silvertown.domain.reminder.mapper;

import com.silvertown.domain.reminder.vo.ReminderVo;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReminderMapper {
    List<ReminderVo> findOwnedByCondition(
            @Param("userId") String userId,
            @Param("status") String status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    int existsOwnedBill(@Param("userId") String userId, @Param("billId") String billId);

    int insert(ReminderVo reminder);
}
