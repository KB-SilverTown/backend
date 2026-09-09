package com.silvertown.domain.voice.mapper;

import com.silvertown.domain.voice.vo.VoiceStreamTicketVo;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface VoiceStreamTicketMapper {
    int insert(VoiceStreamTicketVo voiceStreamTicket);

    VoiceStreamTicketVo findUnusedUnexpiredByHash(
            @Param("ticketHash") String ticketHash, @Param("now") LocalDateTime now);

    int consumeIfUnusedAndUnexpired(
            @Param("ticketId") String ticketId,
            @Param("now") LocalDateTime now,
            @Param("usedAt") LocalDateTime usedAt);
}
