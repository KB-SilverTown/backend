package com.silvertown.domain.voice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvertown.domain.voice.dto.VoiceSettingsRequest;
import com.silvertown.domain.voice.dto.VoiceSettingsResponse;
import com.silvertown.domain.voice.mapper.UserVoiceSettingsMapper;
import com.silvertown.domain.voice.service.impl.VoiceSettingsServiceImpl;
import com.silvertown.domain.voice.vo.UserVoiceSettingsVo;
import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class VoiceSettingsServiceImplTest {
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-02T01:00:00Z"), ZoneId.of("Asia/Seoul"));

    private final ObjectMapper objectMapper = new ObjectMapper();
    private UserVoiceSettingsMapper userVoiceSettingsMapper;
    private VoiceSettingsService voiceSettingsService;

    @BeforeEach
    void setUp() {
        userVoiceSettingsMapper = Mockito.mock(UserVoiceSettingsMapper.class);
        voiceSettingsService = new VoiceSettingsServiceImpl(userVoiceSettingsMapper, CLOCK);
    }

    @Test
    void returnsSafeDefaultsWithoutPersistingWhenNoSettingExists() {
        when(userVoiceSettingsMapper.findByUserId(USER_ID.toString())).thenReturn(null);

        VoiceSettingsResponse response = voiceSettingsService.get(USER_ID.toString());

        assertEquals("ko-KR-JiMinNeural", response.getTtsVoice());
        assertEquals(new BigDecimal("1.05"), response.getSpeechRateMultiplier());
        assertEquals(new BigDecimal("0.97"), response.getPitchMultiplier());
        assertEquals(new BigDecimal("1.00"), response.getVolumeMultiplier());
        assertNull(response.getUpdatedAt());
        verify(userVoiceSettingsMapper).findByUserId(USER_ID.toString());
    }

    @Test
    void delegatesOnlyRequestedFieldsToAtomicDatabaseMerge() throws Exception {
        UserVoiceSettingsVo stored = settings(
                "ko-KR-GookMinNeural", "1.20", "1.10", LocalDateTime.of(2026, 9, 2, 10, 0));
        when(userVoiceSettingsMapper.findByUserId(USER_ID.toString())).thenReturn(stored);

        VoiceSettingsResponse response = voiceSettingsService.update(
                USER_ID.toString(), request("{\"speechRateMultiplier\":1.20}"));

        ArgumentCaptor<UserVoiceSettingsVo> captor = ArgumentCaptor.forClass(UserVoiceSettingsVo.class);
        verify(userVoiceSettingsMapper).upsert(captor.capture());
        UserVoiceSettingsVo update = captor.getValue();
        assertEquals(USER_ID.toString(), update.getUserId());
        assertNull(update.getVoiceName());
        assertEquals(new BigDecimal("1.20"), update.getSpeechRateMultiplier());
        assertNull(update.getVolumeMultiplier());
        assertEquals(new BigDecimal("0.97"), response.getPitchMultiplier());
        assertEquals(LocalDateTime.of(2026, 9, 2, 10, 0).atZone(CLOCK.getZone()).toOffsetDateTime(),
                response.getUpdatedAt());
    }

    @Test
    void normalizesUnsupportedStoredSettingsToSafeDefaults() {
        when(userVoiceSettingsMapper.findByUserId(USER_ID.toString())).thenReturn(settings(
                "ko-KR-UnsupportedNeural", "0.50", "1.50", LocalDateTime.of(2026, 9, 2, 9, 0)));

        VoiceSettingsResponse response = voiceSettingsService.get(USER_ID.toString());

        assertEquals("ko-KR-JiMinNeural", response.getTtsVoice());
        assertEquals(new BigDecimal("1.05"), response.getSpeechRateMultiplier());
        assertEquals(new BigDecimal("1.00"), response.getVolumeMultiplier());
    }

    @Test
    void rejectsEmptyRequestWhenServiceIsCalledDirectly() throws Exception {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> voiceSettingsService.update(USER_ID.toString(), request("{}")));

        assertEquals(ErrorCode.INVALID_REQUEST, exception.getErrorCode());
    }

    private VoiceSettingsRequest request(String json) throws Exception {
        return objectMapper.readValue(json, VoiceSettingsRequest.class);
    }

    private UserVoiceSettingsVo settings(
            String voiceName,
            String speechRateMultiplier,
            String volumeMultiplier,
            LocalDateTime updatedAt
    ) {
        UserVoiceSettingsVo settings = new UserVoiceSettingsVo();
        settings.setUserId(USER_ID.toString());
        settings.setVoiceName(voiceName);
        settings.setSpeechRateMultiplier(new BigDecimal(speechRateMultiplier));
        settings.setVolumeMultiplier(new BigDecimal(volumeMultiplier));
        settings.setUpdatedAt(updatedAt);
        return settings;
    }
}
