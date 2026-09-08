package com.silvertown.domain.voice.mapper;

import com.silvertown.domain.voice.vo.VoiceUiActionVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface VoiceUiActionMapper {
    VoiceUiActionVo findByActionId(@Param("actionId") String actionId);

    int insert(VoiceUiActionVo action);
}
