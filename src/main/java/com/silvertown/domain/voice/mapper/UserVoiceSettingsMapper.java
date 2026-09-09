package com.silvertown.domain.voice.mapper;

import com.silvertown.domain.voice.vo.UserVoiceSettingsVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserVoiceSettingsMapper {
    UserVoiceSettingsVo findByUserId(@Param("userId") String userId);

    int upsert(UserVoiceSettingsVo userVoiceSettings);
}
