package com.silvertown.domain.bill.mapper;

import com.silvertown.domain.bill.vo.BillVo;
import com.silvertown.domain.bill.vo.BillPaymentVo;
import com.silvertown.domain.bill.vo.BillMonthlyAggregateVo;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BillMapper {
    int existsOwnedVoiceSession(@Param("userId") String userId, @Param("voiceSessionId") String voiceSessionId);

    int insert(BillVo bill);

    BillVo findOwnedById(@Param("userId") String userId, @Param("billId") String billId);

    BillVo findOwnedByIdForUpdate(@Param("userId") String userId, @Param("billId") String billId);

    List<BillVo> findOwnedByCondition(
            @Param("userId") String userId,
            @Param("status") String status,
            @Param("offset") int offset,
            @Param("size") int size);

    long countOwnedByCondition(@Param("userId") String userId, @Param("status") String status);

    BillMonthlyAggregateVo summarizeOwnedByDueDateRange(
            @Param("userId") String userId,
            @Param("startDueDate") LocalDate startDueDate,
            @Param("lastDueDate") LocalDate lastDueDate);

    List<BillVo> findOwnedByDueDateRange(
            @Param("userId") String userId,
            @Param("startDueDate") LocalDate startDueDate,
            @Param("lastDueDate") LocalDate lastDueDate,
            @Param("size") int size);

    int updateForReconfirm(
            @Param("userId") String userId,
            @Param("billId") String billId,
            @Param("payee") String payee,
            @Param("amount") Long amount,
            @Param("dueDate") LocalDate dueDate);

    int cancelForOwner(@Param("userId") String userId, @Param("billId") String billId);

    int confirm(
            @Param("userId") String userId,
            @Param("billId") String billId,
            @Param("confirmationTokenHash") String confirmationTokenHash,
            @Param("confirmationTokenExpiresAt") LocalDateTime confirmationTokenExpiresAt,
            @Param("confirmedAt") LocalDateTime confirmedAt);

    int insertPayment(BillPaymentVo payment);

    int markPaid(@Param("userId") String userId, @Param("billId") String billId);
}
