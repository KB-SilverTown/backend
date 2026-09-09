package com.silvertown.domain.voice.service;

import com.silvertown.domain.voice.adaptation.VoiceAdaptationState;
import com.silvertown.domain.voice.adaptation.VoiceGuidanceCommand;
import com.silvertown.domain.voice.enums.VoiceGuidanceMode;
import com.silvertown.domain.voice.mapper.UserVoiceSettingsMapper;
import com.silvertown.domain.voice.vo.UserVoiceSettingsVo;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists only explicit guidance preferences and aggregate session support signals. */
@Service
@RequiredArgsConstructor
public class VoiceGuidanceSettingsService {
    private static final BigDecimal RATE_DEFAULT = new BigDecimal("1.05");
    private static final BigDecimal VOLUME_DEFAULT = new BigDecimal("1.00");
    private static final BigDecimal MIN_RATE = new BigDecimal("0.90");
    private static final BigDecimal MAX_RATE = new BigDecimal("1.20");
    private static final BigDecimal MIN_VOLUME = new BigDecimal("1.00");
    private static final BigDecimal MAX_VOLUME = new BigDecimal("1.20");
    private static final BigDecimal STEP = new BigDecimal("0.05");

    private final UserVoiceSettingsMapper userVoiceSettingsMapper;

    /**
     * Determines the voice guidance mode for a new session.
     *
     * @param userId the user whose preferred mode is retrieved
     * @return the scheduled support mode, compact mode, or standard mode
     */
    @Transactional
    public VoiceGuidanceMode initialMode(String userId) {
        if (userVoiceSettingsMapper == null) {
            return VoiceGuidanceMode.STANDARD;
        }
        UserVoiceSettingsVo settings = userVoiceSettingsMapper.findByUserId(userId);
        if (settings == null) {
            return VoiceGuidanceMode.STANDARD;
        }
        if (Boolean.TRUE.equals(settings.getSupportStartNextSession())) {
            settings.setSupportStartNextSession(false);
            userVoiceSettingsMapper.upsert(settings);
            return VoiceGuidanceMode.SUPPORT;
        }
        return VoiceGuidanceMode.COMPACT.name().equals(settings.getPreferredVerbosity())
                ? VoiceGuidanceMode.COMPACT : VoiceGuidanceMode.STANDARD;
    }

    /**
     * Applies an explicit voice guidance adjustment and saves the updated preferences.
     *
     * @param userId  the user whose preferences are updated
     * @param command the voice guidance adjustment to apply
     * @return {@code true} if a louder command reaches the maximum volume, {@code false} otherwise
     */
    @Transactional
    public boolean applyExplicitCommand(String userId, VoiceGuidanceCommand command) {
        if (userVoiceSettingsMapper == null) {
            return false;
        }
        UserVoiceSettingsVo settings = defaults(userId, userVoiceSettingsMapper.findByUserId(userId));
        switch (command) {
            case SLOWER -> settings.setSpeechRateMultiplier(clamp(settings.getSpeechRateMultiplier().subtract(STEP), MIN_RATE, MAX_RATE));
            case FASTER -> {
                settings.setSpeechRateMultiplier(clamp(settings.getSpeechRateMultiplier().add(STEP), MIN_RATE, MAX_RATE));
                settings.setPreferredVerbosity(VoiceGuidanceMode.COMPACT.name());
            }
            case DEFAULT_SPEED -> {
                settings.setSpeechRateMultiplier(RATE_DEFAULT);
                settings.setPreferredVerbosity(VoiceGuidanceMode.STANDARD.name());
            }
            case LOUDER -> settings.setVolumeMultiplier(clamp(settings.getVolumeMultiplier().add(STEP), MIN_VOLUME, MAX_VOLUME));
            case QUIETER -> settings.setVolumeMultiplier(clamp(settings.getVolumeMultiplier().subtract(STEP), MIN_VOLUME, MAX_VOLUME));
            case DEFAULT_VOLUME -> settings.setVolumeMultiplier(VOLUME_DEFAULT);
        }
        userVoiceSettingsMapper.upsert(settings);
        return command == VoiceGuidanceCommand.LOUDER
                && settings.getVolumeMultiplier().compareTo(MAX_VOLUME) == 0;
    }

    /**
     * Persists the completed session's support signals and updates support mode for the next session.
     *
     * @param userId the user whose voice guidance settings are updated
     * @param state  the completed session's voice adaptation state
     */
    @Transactional
    public void completeSession(String userId, VoiceAdaptationState state) {
        if (userVoiceSettingsMapper == null || state == null) {
            return;
        }
        UserVoiceSettingsVo settings = defaults(userId, userVoiceSettingsMapper.findByUserId(userId));
        int signalCount = state.supportSignalCount();
        settings.setRecentSupportSignalCount(signalCount);
        if (signalCount >= 2) {
            settings.setSupportStartNextSession(true);
        } else if (signalCount == 0 && state.mode() == VoiceGuidanceMode.SUPPORT) {
            settings.setSupportStartNextSession(false);
        }
        userVoiceSettingsMapper.upsert(settings);
    }

    /**
     * Initializes voice guidance settings for a user, preserving existing values and filling in missing preferences with defaults.
     *
     * @param userId the user identifier to assign to the settings
     * @param source existing settings to update, or {@code null} to create new settings
     * @return the initialized voice guidance settings
     */
    private UserVoiceSettingsVo defaults(String userId, UserVoiceSettingsVo source) {
        UserVoiceSettingsVo settings = source == null ? new UserVoiceSettingsVo() : source;
        settings.setUserId(userId);
        if (settings.getSpeechRateMultiplier() == null) settings.setSpeechRateMultiplier(RATE_DEFAULT);
        if (settings.getVolumeMultiplier() == null) settings.setVolumeMultiplier(VOLUME_DEFAULT);
        if (settings.getPreferredVerbosity() == null) settings.setPreferredVerbosity(VoiceGuidanceMode.STANDARD.name());
        if (settings.getSupportStartNextSession() == null) settings.setSupportStartNextSession(false);
        if (settings.getRecentSupportSignalCount() == null) settings.setRecentSupportSignalCount(0);
        return settings;
    }

    /**
     * Restricts a value to the inclusive range between the specified minimum and maximum.
     *
     * @param value the value to restrict
     * @param min   the minimum allowed value
     * @param max   the maximum allowed value
     * @return      the minimum when the value is below it, the maximum when the value is above it, or the original value otherwise
     */
    private BigDecimal clamp(BigDecimal value, BigDecimal min, BigDecimal max) {
        return value.compareTo(min) < 0 ? min : value.compareTo(max) > 0 ? max : value;
    }
}
