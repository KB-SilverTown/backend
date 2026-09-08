package com.silvertown.domain.voice.mapper;

import com.silvertown.domain.voice.vo.VoiceInteractionCardVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface VoiceInteractionCardMapper {
    VoiceInteractionCardVo findBySessionId(@Param("sessionId") String sessionId);

    VoiceInteractionCardVo findBySessionIdForUpdate(@Param("sessionId") String sessionId);

    int insert(VoiceInteractionCardVo card);

    int replace(VoiceInteractionCardVo card);

    int deactivateActiveBySessionId(@Param("sessionId") String sessionId);
}
