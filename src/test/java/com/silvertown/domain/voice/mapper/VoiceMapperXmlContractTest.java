package com.silvertown.domain.voice.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
class VoiceMapperXmlContractTest {
    @Test
    void voiceMapperXmlsDeclareAllPersistenceStatements() throws Exception {
        Configuration configuration = new Configuration();
        configuration.addMapper(UserVoiceSettingsMapper.class);
        configuration.addMapper(IdempotencyRecordMapper.class);

        for (String resource : List.of(
                "mapper/voice/UserVoiceSettingsMapper.xml",
                "mapper/voice/IdempotencyRecordMapper.xml")) {
            try (Reader reader = Resources.getResourceAsReader(resource)) {
                new XMLMapperBuilder(
                        reader, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }

        assertTrue(configuration.hasStatement(
                "com.silvertown.domain.voice.mapper.UserVoiceSettingsMapper.upsert"));
        assertTrue(configuration.hasStatement(
                "com.silvertown.domain.voice.mapper.IdempotencyRecordMapper.complete"));
    }

    @Test
    void voiceSettingsMapperUsesMultiplierColumns() throws Exception {
        String xml;
        try (InputStream input = Resources.getResourceAsStream("mapper/voice/UserVoiceSettingsMapper.xml")) {
            xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(xml.contains("speech_rate_multiplier"));
        assertTrue(xml.contains("volume_multiplier"));
    }

}
