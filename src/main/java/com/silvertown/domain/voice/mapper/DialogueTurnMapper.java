package com.silvertown.domain.voice.mapper;

import com.silvertown.domain.voice.vo.DialogueTurnVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DialogueTurnMapper {
    int insert(DialogueTurnVo dialogueTurn);

    int findNextSequenceNo(@Param("sessionId") String sessionId);

    DialogueTurnVo findBySessionIdAndTurnId(
            @Param("sessionId") String sessionId, @Param("turnId") String turnId);

    DialogueTurnVo findBySessionIdAndSequenceNo(
            @Param("sessionId") String sessionId, @Param("sequenceNo") int sequenceNo);

    DialogueTurnVo findLatestBySessionId(@Param("sessionId") String sessionId);

    DialogueTurnVo findLatestBySessionIdForUpdate(@Param("sessionId") String sessionId);

    DialogueTurnVo findLatestBusinessAiTurn(@Param("sessionId") String sessionId);

    DialogueTurnVo findLatestReplayableAiTurn(@Param("sessionId") String sessionId);

    int incrementReplayCount(@Param("turnId") String turnId);

    int markInterrupted(@Param("turnId") String turnId);

    int recordSilenceMs(@Param("turnId") String turnId, @Param("silenceMs") int silenceMs);

    int updateDisplayCard(@Param("turnId") String turnId, @Param("displayCard") String displayCard);
}
