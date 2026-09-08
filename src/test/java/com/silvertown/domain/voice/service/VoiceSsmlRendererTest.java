package com.silvertown.domain.voice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.silvertown.domain.voice.mapper.UserVoiceSettingsMapper;
import com.silvertown.domain.voice.vo.UserVoiceSettingsVo;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class VoiceSsmlRendererTest {
    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";

    private UserVoiceSettingsMapper userVoiceSettingsMapper;
    private VoiceSsmlRenderer renderer;

    @BeforeEach
    void setUp() {
        userVoiceSettingsMapper = Mockito.mock(UserVoiceSettingsMapper.class);
        renderer = new VoiceSsmlRenderer(userVoiceSettingsMapper);
    }

    @Test
    void rendersSavedVoiceSettingsInCommonSsml() {
        when(userVoiceSettingsMapper.findByUserId(USER_ID)).thenReturn(settings(
                "ko-KR-GookMinNeural", "1.20", "1.20"));

        String ssml = renderer.render(USER_ID, "안내 문구입니다.");

        assertEquals("<speak version=\"1.0\" xml:lang=\"ko-KR\" xmlns=\"http://www.w3.org/2001/10/synthesis\">"
                        + "<voice name=\"ko-KR-GookMinNeural\"><prosody rate=\"1.20\" pitch=\"-3%\" volume=\"+20%\">"
                        + "안내 문구입니다.</prosody></voice></speak>",
                ssml);
    }

    @Test
    void normalizesMissingOrInvalidStoredSettingsToSafeDefaults() {
        UserVoiceSettingsVo storedSettings = settings("ko-KR-UnsupportedNeural", "0.89", "1.21");
        when(userVoiceSettingsMapper.findByUserId(USER_ID)).thenReturn(storedSettings);

        String ssml = renderer.render(USER_ID, "기본 안내");

        assertEquals("<speak version=\"1.0\" xml:lang=\"ko-KR\" xmlns=\"http://www.w3.org/2001/10/synthesis\">"
                        + "<voice name=\"ko-KR-JiMinNeural\"><prosody rate=\"1.05\" pitch=\"-3%\" volume=\"100\">"
                        + "기본 안내</prosody></voice></speak>",
                ssml);
        assertEquals("ko-KR-UnsupportedNeural", storedSettings.getVoiceName());
        assertEquals(new BigDecimal("0.89"), storedSettings.getSpeechRateMultiplier());
        assertEquals(new BigDecimal("1.21"), storedSettings.getVolumeMultiplier());
    }

    @Test
    void escapesAllXmlSpecialCharactersInTtsText() {
        when(userVoiceSettingsMapper.findByUserId(USER_ID)).thenReturn(null);

        String ssml = renderer.render(USER_ID, "& < > \" '");

        assertTrue(ssml.contains("&amp; &lt; &gt; &quot; &apos;"));
    }

    @Test
    void removesInvalidXmlCharactersAndPreservesValidSupplementaryCharacters() {
        when(userVoiceSettingsMapper.findByUserId(USER_ID)).thenReturn(null);

        String ssml = renderer.render(USER_ID, "앞\u0001뒤😀");

        assertTrue(ssml.contains("앞뒤😀"));
    }

    private UserVoiceSettingsVo settings(String voiceName, String rate, String volume) {
        UserVoiceSettingsVo settings = new UserVoiceSettingsVo();
        settings.setVoiceName(voiceName);
        settings.setSpeechRateMultiplier(new BigDecimal(rate));
        settings.setVolumeMultiplier(new BigDecimal(volume));
        return settings;
    }
}
