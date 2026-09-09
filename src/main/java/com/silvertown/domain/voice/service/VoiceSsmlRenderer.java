package com.silvertown.domain.voice.service;

import com.silvertown.domain.voice.mapper.UserVoiceSettingsMapper;
import com.silvertown.domain.voice.vo.UserVoiceSettingsVo;
import java.math.BigDecimal;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Renders the server-owned Azure Speech SSML from saved, safe user voice settings. */
@Component
@RequiredArgsConstructor
public class VoiceSsmlRenderer {
    private static final String DEFAULT_TTS_VOICE = "ko-KR-JiMinNeural";
    private static final BigDecimal DEFAULT_SPEECH_RATE_MULTIPLIER = new BigDecimal("1.05");
    private static final BigDecimal PITCH_MULTIPLIER = new BigDecimal("0.97");
    private static final BigDecimal DEFAULT_VOLUME_MULTIPLIER = new BigDecimal("1.00");
    private static final BigDecimal MIN_SPEECH_RATE_MULTIPLIER = new BigDecimal("0.90");
    private static final BigDecimal MAX_SPEECH_RATE_MULTIPLIER = new BigDecimal("1.20");
    private static final BigDecimal MIN_VOLUME_MULTIPLIER = new BigDecimal("1.00");
    private static final BigDecimal MAX_VOLUME_MULTIPLIER = new BigDecimal("1.20");
    private static final Set<String> ALLOWED_TTS_VOICES = Set.of(
            DEFAULT_TTS_VOICE, "ko-KR-GookMinNeural");

    private final UserVoiceSettingsMapper userVoiceSettingsMapper;

    public String render(String userId, String ttsText) {
        UserVoiceSettingsVo settings = normalize(userVoiceSettingsMapper.findByUserId(userId));
        return "<speak version=\"1.0\" xml:lang=\"ko-KR\" xmlns=\"http://www.w3.org/2001/10/synthesis\">"
                + "<voice name=\"" + settings.getVoiceName() + "\">"
                + "<prosody rate=\"" + decimal(settings.getSpeechRateMultiplier())
                + "\" pitch=\"" + pitch(PITCH_MULTIPLIER)
                + "\" volume=\"" + volume(settings.getVolumeMultiplier()) + "\">"
                + escapeXml(ttsText)
                + "</prosody></voice></speak>";
    }

    private UserVoiceSettingsVo normalize(UserVoiceSettingsVo settings) {
        UserVoiceSettingsVo normalized = defaultSettings();
        if (settings == null) {
            return normalized;
        }

        if (ALLOWED_TTS_VOICES.contains(settings.getVoiceName())) {
            normalized.setVoiceName(settings.getVoiceName());
        }
        if (isWithinRange(
                settings.getSpeechRateMultiplier(),
                MIN_SPEECH_RATE_MULTIPLIER,
                MAX_SPEECH_RATE_MULTIPLIER)) {
            normalized.setSpeechRateMultiplier(settings.getSpeechRateMultiplier());
        }
        if (isWithinRange(
                settings.getVolumeMultiplier(),
                MIN_VOLUME_MULTIPLIER,
                MAX_VOLUME_MULTIPLIER)) {
            normalized.setVolumeMultiplier(settings.getVolumeMultiplier());
        }
        return normalized;
    }

    private UserVoiceSettingsVo defaultSettings() {
        UserVoiceSettingsVo settings = new UserVoiceSettingsVo();
        settings.setVoiceName(DEFAULT_TTS_VOICE);
        settings.setSpeechRateMultiplier(DEFAULT_SPEECH_RATE_MULTIPLIER);
        settings.setVolumeMultiplier(DEFAULT_VOLUME_MULTIPLIER);
        return settings;
    }

    private boolean isWithinRange(BigDecimal value, BigDecimal minimum, BigDecimal maximum) {
        return value != null && value.compareTo(minimum) >= 0 && value.compareTo(maximum) <= 0;
    }

    private String decimal(BigDecimal value) {
        return value.toPlainString();
    }

    private String volume(BigDecimal multiplier) {
        if (BigDecimal.ONE.compareTo(multiplier) == 0) {
            return "100";
        }
        BigDecimal percent = multiplier.subtract(BigDecimal.ONE)
                .movePointRight(2)
                .stripTrailingZeros();
        return "+" + percent.toPlainString() + "%";
    }

    private String pitch(BigDecimal multiplier) {
        BigDecimal percent = multiplier.subtract(BigDecimal.ONE)
                .movePointRight(2)
                .stripTrailingZeros();
        if (percent.signum() >= 0) {
            return "+" + percent.toPlainString() + "%";
        }
        return percent.toPlainString() + "%";
    }

    private String escapeXml(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length());
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (!isValidXml10Character(codePoint)) {
                continue;
            }
            switch (codePoint) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '\"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&apos;");
                default -> escaped.appendCodePoint(codePoint);
            }
        }
        return escaped.toString();
    }

    private boolean isValidXml10Character(int codePoint) {
        return codePoint == '\t'
                || codePoint == '\n'
                || codePoint == '\r'
                || (codePoint >= 0x20 && codePoint <= 0xD7FF)
                || (codePoint >= 0xE000 && codePoint <= 0xFFFD)
                || (codePoint >= 0x10000 && codePoint <= 0x10FFFF);
    }
}
