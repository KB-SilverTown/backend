package com.silvertown.domain.voice.service.impl;

import com.silvertown.domain.voice.dto.VoiceSettingsRequest;
import com.silvertown.domain.voice.dto.VoiceSettingsResponse;
import com.silvertown.domain.voice.mapper.UserVoiceSettingsMapper;
import com.silvertown.domain.voice.service.VoiceSettingsService;
import com.silvertown.domain.voice.vo.UserVoiceSettingsVo;
import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VoiceSettingsServiceImpl implements VoiceSettingsService {
    private static final String DEFAULT_TTS_VOICE = "ko-KR-JiMinNeural";
    private static final BigDecimal DEFAULT_SPEECH_RATE_MULTIPLIER = new BigDecimal("1.05");
    private static final BigDecimal DEFAULT_PITCH_MULTIPLIER = new BigDecimal("0.97");
    private static final BigDecimal DEFAULT_VOLUME_MULTIPLIER = new BigDecimal("1.00");
    private static final BigDecimal MIN_SPEECH_RATE_MULTIPLIER = new BigDecimal("0.90");
    private static final BigDecimal MAX_SPEECH_RATE_MULTIPLIER = new BigDecimal("1.20");
    private static final BigDecimal MIN_VOLUME_MULTIPLIER = new BigDecimal("1.00");
    private static final BigDecimal MAX_VOLUME_MULTIPLIER = new BigDecimal("1.20");
    private static final Set<String> ALLOWED_TTS_VOICES = Set.of(
            DEFAULT_TTS_VOICE, "ko-KR-GookMinNeural");

    private final UserVoiceSettingsMapper userVoiceSettingsMapper;
    private final Clock clock;

    @Override
    public VoiceSettingsResponse get(String userId) {
        return toResponse(normalize(userVoiceSettingsMapper.findByUserId(userId)));
    }

    @Override
    @Transactional
    public VoiceSettingsResponse update(String userId, VoiceSettingsRequest request) {
        validateRequest(request);

        UserVoiceSettingsVo requestedSettings = new UserVoiceSettingsVo();
        requestedSettings.setUserId(userId);
        requestedSettings.setVoiceName(request.getTtsVoice());
        requestedSettings.setSpeechRateMultiplier(request.getSpeechRateMultiplier());
        requestedSettings.setVolumeMultiplier(request.getVolumeMultiplier());

        userVoiceSettingsMapper.upsert(requestedSettings);
        return toResponse(normalize(userVoiceSettingsMapper.findByUserId(userId)));
    }

    private void validateRequest(VoiceSettingsRequest request) {
        if (!request.isAnySettingProvided()
                || (request.getTtsVoice() != null && !ALLOWED_TTS_VOICES.contains(request.getTtsVoice()))
                || (request.getSpeechRateMultiplier() != null && !isWithinRange(
                        request.getSpeechRateMultiplier(),
                        MIN_SPEECH_RATE_MULTIPLIER,
                        MAX_SPEECH_RATE_MULTIPLIER))
                || (request.getVolumeMultiplier() != null && !isWithinRange(
                        request.getVolumeMultiplier(),
                        MIN_VOLUME_MULTIPLIER,
                        MAX_VOLUME_MULTIPLIER))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    private UserVoiceSettingsVo normalize(UserVoiceSettingsVo settings) {
        if (settings == null) {
            return defaults();
        }

        settings.setVoiceName(ALLOWED_TTS_VOICES.contains(settings.getVoiceName())
                ? settings.getVoiceName() : DEFAULT_TTS_VOICE);
        if (!isWithinRange(
                settings.getSpeechRateMultiplier(),
                MIN_SPEECH_RATE_MULTIPLIER,
                MAX_SPEECH_RATE_MULTIPLIER)) {
            settings.setSpeechRateMultiplier(DEFAULT_SPEECH_RATE_MULTIPLIER);
        }
        if (!isWithinRange(
                settings.getVolumeMultiplier(),
                MIN_VOLUME_MULTIPLIER,
                MAX_VOLUME_MULTIPLIER)) {
            settings.setVolumeMultiplier(DEFAULT_VOLUME_MULTIPLIER);
        }
        return settings;
    }

    private boolean isWithinRange(BigDecimal value, BigDecimal minimum, BigDecimal maximum) {
        return value != null && value.compareTo(minimum) >= 0 && value.compareTo(maximum) <= 0;
    }

    private UserVoiceSettingsVo defaults() {
        UserVoiceSettingsVo defaults = new UserVoiceSettingsVo();
        defaults.setVoiceName(DEFAULT_TTS_VOICE);
        defaults.setSpeechRateMultiplier(DEFAULT_SPEECH_RATE_MULTIPLIER);
        defaults.setVolumeMultiplier(DEFAULT_VOLUME_MULTIPLIER);
        return defaults;
    }

    private VoiceSettingsResponse toResponse(UserVoiceSettingsVo settings) {
        OffsetDateTime updatedAt = settings.getUpdatedAt() == null
                ? null : settings.getUpdatedAt().atZone(clock.getZone()).toOffsetDateTime();
        return new VoiceSettingsResponse(
                settings.getVoiceName(),
                settings.getSpeechRateMultiplier(),
                DEFAULT_PITCH_MULTIPLIER,
                settings.getVolumeMultiplier(),
                updatedAt);
    }
}
