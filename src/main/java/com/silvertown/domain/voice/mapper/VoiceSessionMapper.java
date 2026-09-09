package com.silvertown.domain.voice.mapper;

import com.silvertown.domain.voice.vo.VoiceSessionVo;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface VoiceSessionMapper {
    int insert(VoiceSessionVo voiceSession);

    VoiceSessionVo findOwnedById(
            @Param("userId") String userId, @Param("sessionId") String sessionId);

    VoiceSessionVo findOwnedByIdForUpdate(
            @Param("userId") String userId, @Param("sessionId") String sessionId);

    List<VoiceSessionVo> findActiveInteractiveSessions(@Param("now") LocalDateTime now);

    int claimForTurn(
            @Param("userId") String userId,
            @Param("sessionId") String sessionId,
            @Param("now") LocalDateTime now);

    int completeTurn(
            @Param("userId") String userId,
            @Param("sessionId") String sessionId,
            @Param("currentStep") String currentStep);

    int restoreTurnClaim(
            @Param("userId") String userId,
            @Param("sessionId") String sessionId,
            @Param("previousStatus") String previousStatus);

    int updateTransferId(
            @Param("userId") String userId,
            @Param("sessionId") String sessionId,
            @Param("transferId") String transferId);

    int updateStatusAndStep(
            @Param("userId") String userId,
            @Param("sessionId") String sessionId,
            @Param("status") String status,
            @Param("currentStep") String currentStep);

    int closeOwned(
            @Param("userId") String userId,
            @Param("sessionId") String sessionId,
            @Param("currentStep") String currentStep,
            @Param("endedAt") LocalDateTime endedAt);

    int claimStreamInputTurn(
            @Param("userId") String userId,
            @Param("sessionId") String sessionId,
            @Param("inputTurnId") String inputTurnId,
            @Param("now") LocalDateTime now);

    int beginStreamFinalProcessing(
            @Param("userId") String userId,
            @Param("sessionId") String sessionId,
            @Param("inputTurnId") String inputTurnId,
            @Param("lifecycleGeneration") long lifecycleGeneration,
            @Param("now") LocalDateTime now);

    int completeStreamTurnWithAi(
            @Param("userId") String userId,
            @Param("sessionId") String sessionId,
            @Param("inputTurnId") String inputTurnId,
            @Param("lifecycleGeneration") long lifecycleGeneration,
            @Param("aiTurnId") String aiTurnId,
            @Param("currentStep") String currentStep,
            @Param("now") LocalDateTime now);

    int interruptActiveAiTurn(
            @Param("userId") String userId,
            @Param("sessionId") String sessionId,
            @Param("aiTurnId") String aiTurnId,
            @Param("lifecycleGeneration") long lifecycleGeneration,
            @Param("now") LocalDateTime now);

    int cancelActiveInputTurn(
            @Param("userId") String userId,
            @Param("sessionId") String sessionId,
            @Param("inputTurnId") String inputTurnId,
            @Param("lifecycleGeneration") long lifecycleGeneration,
            @Param("now") LocalDateTime now);
}
