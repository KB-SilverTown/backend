package com.silvertown.domain.risk.mapper;

import com.silvertown.domain.risk.vo.RiskAssessment;
import com.silvertown.domain.risk.vo.RiskCheck;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RiskScoreMapper {
    OffsetDateTime findLastSuccessfulTransferAt(
            @Param("userId") String userId, @Param("recipientId") String recipientId);

    List<Long> findRecentUserAmounts(@Param("userId") String userId);

    List<Long> findRecentRecipientAmounts(
            @Param("userId") String userId, @Param("recipientId") String recipientId);

    int countRecentAttempts(@Param("userId") String userId, @Param("recipientId") String recipientId);

    int countRecentDistinctRecipients(@Param("userId") String userId);

    int insertAssessment(RiskAssessment assessment);

    int insertRiskCheck(RiskCheck riskCheck);

    int holdTransfer(@Param("userId") String userId, @Param("transferId") String transferId);
}
