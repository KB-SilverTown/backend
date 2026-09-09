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
        configuration.addMapper(VoiceSessionMapper.class);
        configuration.addMapper(DialogueTurnMapper.class);
        configuration.addMapper(IdempotencyRecordMapper.class);

        for (String resource : List.of(
                "mapper/voice/UserVoiceSettingsMapper.xml",
                "mapper/voice/VoiceSessionMapper.xml",
                "mapper/voice/DialogueTurnMapper.xml",
                "mapper/voice/IdempotencyRecordMapper.xml")) {
            try (Reader reader = Resources.getResourceAsReader(resource)) {
                new XMLMapperBuilder(
                        reader, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }

        assertTrue(configuration.hasStatement(
                "com.silvertown.domain.voice.mapper.UserVoiceSettingsMapper.upsert"));
        assertTrue(configuration.hasStatement(
                "com.silvertown.domain.voice.mapper.VoiceSessionMapper.closeOwned"));
        assertTrue(configuration.hasStatement(
                "com.silvertown.domain.voice.mapper.VoiceSessionMapper.findOwnedByIdForUpdate"));
        assertTrue(configuration.hasStatement(
                "com.silvertown.domain.voice.mapper.VoiceSessionMapper.findActiveInteractiveSessions"));
        assertTrue(configuration.hasStatement(
                "com.silvertown.domain.voice.mapper.VoiceSessionMapper.claimForTurn"));
        assertTrue(configuration.hasStatement(
                "com.silvertown.domain.voice.mapper.VoiceSessionMapper.completeTurn"));
        assertTrue(configuration.hasStatement(
                "com.silvertown.domain.voice.mapper.VoiceSessionMapper.restoreTurnClaim"));
        assertTrue(configuration.hasStatement(
                "com.silvertown.domain.voice.mapper.DialogueTurnMapper.findBySessionIdAndTurnId"));
        assertTrue(configuration.hasStatement(
                "com.silvertown.domain.voice.mapper.DialogueTurnMapper.findBySessionIdAndSequenceNo"));
        assertTrue(configuration.hasStatement(
                "com.silvertown.domain.voice.mapper.DialogueTurnMapper.findLatestBySessionId"));
        assertTrue(configuration.hasStatement(
                "com.silvertown.domain.voice.mapper.DialogueTurnMapper.findLatestBySessionIdForUpdate"));
        assertTrue(configuration.hasStatement(
                "com.silvertown.domain.voice.mapper.DialogueTurnMapper.findLatestBusinessAiTurn"));
        assertTrue(configuration.hasStatement(
                "com.silvertown.domain.voice.mapper.DialogueTurnMapper.findLatestReplayableAiTurn"));
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

    @Test
    void updatingStatusDoesNotReopenTerminalSessions() throws Exception {
        String xml;
        try (InputStream input = Resources.getResourceAsStream("mapper/voice/VoiceSessionMapper.xml")) {
            xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        int queryStart = xml.indexOf("<update id=\"updateStatusAndStep\"");
        int queryEnd = xml.indexOf("</update>", queryStart);
        assertTrue(queryStart >= 0 && queryEnd > queryStart);

        String updateQuery = xml.substring(queryStart, queryEnd).toUpperCase();
        assertTrue(updateQuery.contains("STATUS NOT IN ('CLOSED', 'EXPIRED')"));
    }

    @Test
    void closingSessionDoesNotUpdateTerminalSessions() throws Exception {
        String xml;
        try (InputStream input = Resources.getResourceAsStream("mapper/voice/VoiceSessionMapper.xml")) {
            xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        int queryStart = xml.indexOf("<update id=\"closeOwned\"");
        int queryEnd = xml.indexOf("</update>", queryStart);
        assertTrue(queryStart >= 0 && queryEnd > queryStart);

        String updateQuery = xml.substring(queryStart, queryEnd).toUpperCase();
        assertTrue(updateQuery.contains("STATUS NOT IN ('CLOSED', 'EXPIRED')"));
    }

    @Test
    void lockingAndTurnClaimStatementsUseTheDocumentedStateRules() throws Exception {
        String xml;
        try (InputStream input = Resources.getResourceAsStream("mapper/voice/VoiceSessionMapper.xml")) {
            xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        int lockStart = xml.indexOf("<select id=\"findOwnedByIdForUpdate\"");
        int lockEnd = xml.indexOf("</select>", lockStart);
        int claimStart = xml.indexOf("<update id=\"claimForTurn\"");
        int claimEnd = xml.indexOf("</update>", claimStart);
        int completeStart = xml.indexOf("<update id=\"completeTurn\"");
        int completeEnd = xml.indexOf("</update>", completeStart);
        int restoreStart = xml.indexOf("<update id=\"restoreTurnClaim\"");
        int restoreEnd = xml.indexOf("</update>", restoreStart);

        assertTrue(lockStart >= 0 && lockEnd > lockStart);
        assertTrue(claimStart >= 0 && claimEnd > claimStart);
        assertTrue(completeStart >= 0 && completeEnd > completeStart);
        assertTrue(restoreStart >= 0 && restoreEnd > restoreStart);
        assertTrue(xml.substring(lockStart, lockEnd).toUpperCase().contains("FOR UPDATE NOWAIT"));
        String claimQuery = xml.substring(claimStart, claimEnd).toUpperCase();
        assertTrue(claimQuery.contains("STATUS IN ('LISTENING', 'SPEAKING')"));
        assertTrue(claimQuery.contains("EXPIRES_AT &GT;"));
        assertTrue(claimQuery.contains("JDBCTYPE=TIMESTAMP"));
        assertTrue(xml.substring(completeStart, completeEnd).toUpperCase()
                .contains("STATUS = 'PROCESSING'"));
        assertTrue(xml.substring(restoreStart, restoreEnd).toUpperCase()
                .contains("STATUS = 'PROCESSING'"));
    }
}
