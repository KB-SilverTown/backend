package com.silvertown.domain.voice.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.silvertown.domain.voice.vo.UserVoiceSettingsVo;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserVoiceSettingsMapperIntegrationTest {
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private SqlSessionFactory sessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        PooledDataSource dataSource = new PooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa", "");
        Configuration configuration = new Configuration(
                new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(UserVoiceSettingsMapper.class);
        try (var mapperXml = Resources.getResourceAsReader("mapper/voice/UserVoiceSettingsMapper.xml")) {
            new org.apache.ibatis.builder.xml.XMLMapperBuilder(
                    mapperXml, configuration, "mapper/voice/UserVoiceSettingsMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        sessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        createSchema(dataSource);
    }

    @Test
    void upsertInsertsAndThenMergesTheSameUsersSettings() throws Exception {
        try (SqlSession session = sessionFactory.openSession()) {
            UserVoiceSettingsMapper mapper = session.getMapper(UserVoiceSettingsMapper.class);
            mapper.upsert(settings("ko-KR-JiMinNeural", "1.05", "1.00"));
            session.commit();

            UserVoiceSettingsVo inserted = mapper.findByUserId(USER_ID.toString());
            assertEquals("ko-KR-JiMinNeural", inserted.getVoiceName());
            assertEquals(new BigDecimal("1.05"), inserted.getSpeechRateMultiplier());
            assertEquals(new BigDecimal("1.00"), inserted.getVolumeMultiplier());
            assertNotNull(inserted.getCreatedAt());
            assertNotNull(inserted.getUpdatedAt());

            mapper.upsert(settings("ko-KR-GookMinNeural", "1.20", "1.10"));
            session.commit();

            UserVoiceSettingsVo updated = mapper.findByUserId(USER_ID.toString());
            assertEquals("ko-KR-GookMinNeural", updated.getVoiceName());
            assertEquals(new BigDecimal("1.20"), updated.getSpeechRateMultiplier());
            assertEquals(new BigDecimal("1.10"), updated.getVolumeMultiplier());
            assertEquals(1, countRows(session));
        }
    }

    @Test
    void upsertPreservesTimestampForIdenticalSettingsAndRefreshesItForChanges() throws Exception {
        try (SqlSession session = sessionFactory.openSession()) {
            UserVoiceSettingsMapper mapper = session.getMapper(UserVoiceSettingsMapper.class);
            UserVoiceSettingsVo request = settings("ko-KR-JiMinNeural", "1.05", "1.00");

            mapper.upsert(request);
            session.commit();

            LocalDateTime previousUpdatedAt = LocalDateTime.of(2000, 1, 1, 0, 0);
            try (PreparedStatement statement = session.getConnection().prepareStatement(
                    "UPDATE user_voice_settings SET updated_at = ? WHERE user_id = ?")) {
                statement.setTimestamp(1, Timestamp.valueOf(previousUpdatedAt));
                statement.setString(2, USER_ID.toString());
                statement.executeUpdate();
            }

            mapper.upsert(request);
            session.commit();

            UserVoiceSettingsVo found = mapper.findByUserId(USER_ID.toString());
            assertEquals("ko-KR-JiMinNeural", found.getVoiceName());
            assertEquals(new BigDecimal("1.05"), found.getSpeechRateMultiplier());
            assertEquals(new BigDecimal("1.00"), found.getVolumeMultiplier());
            assertEquals(previousUpdatedAt, found.getUpdatedAt());

            mapper.upsert(settings("ko-KR-GookMinNeural", "1.05", "1.00"));
            session.commit();

            UserVoiceSettingsVo changed = mapper.findByUserId(USER_ID.toString());
            assertEquals("ko-KR-GookMinNeural", changed.getVoiceName());
            assertEquals(new BigDecimal("1.05"), changed.getSpeechRateMultiplier());
            assertEquals(new BigDecimal("1.00"), changed.getVolumeMultiplier());
            assertTrue(changed.getUpdatedAt().isAfter(previousUpdatedAt));
            assertEquals(1, countRows(session));
        }
    }

    @Test
    void partialUpsertPreservesExistingFieldsNotIncludedInTheRequest() throws Exception {
        try (SqlSession session = sessionFactory.openSession()) {
            UserVoiceSettingsMapper mapper = session.getMapper(UserVoiceSettingsMapper.class);
            UserVoiceSettingsVo initial = settings("ko-KR-GookMinNeural", "1.05", "1.10");
            initial.setPreferredVerbosity("COMPACT");
            initial.setSupportStartNextSession(true);
            initial.setRecentSupportSignalCount(2);
            mapper.upsert(initial);
            session.commit();

            mapper.upsert(settings(null, "1.20", null));
            session.commit();

            UserVoiceSettingsVo found = mapper.findByUserId(USER_ID.toString());
            assertEquals("ko-KR-GookMinNeural", found.getVoiceName());
            assertEquals(new BigDecimal("1.20"), found.getSpeechRateMultiplier());
            assertEquals(new BigDecimal("1.10"), found.getVolumeMultiplier());
            assertEquals("COMPACT", found.getPreferredVerbosity());
            assertTrue(found.getSupportStartNextSession());
            assertEquals(2, found.getRecentSupportSignalCount());
        }
    }

    @Test
    void partialUpsertUsesDefaultsWhenItCreatesTheFirstSettingsRow() throws Exception {
        try (SqlSession session = sessionFactory.openSession()) {
            UserVoiceSettingsMapper mapper = session.getMapper(UserVoiceSettingsMapper.class);
            mapper.upsert(settings(null, "1.20", null));
            session.commit();

            UserVoiceSettingsVo found = mapper.findByUserId(USER_ID.toString());
            assertEquals("ko-KR-JiMinNeural", found.getVoiceName());
            assertEquals(new BigDecimal("1.20"), found.getSpeechRateMultiplier());
            assertEquals(new BigDecimal("1.00"), found.getVolumeMultiplier());
        }
    }

    private int countRows(SqlSession session) throws Exception {
        try (Statement statement = session.getConnection().createStatement();
                var resultSet = statement.executeQuery("SELECT COUNT(*) FROM user_voice_settings")) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private void createSchema(PooledDataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE user_voice_settings ("
                    + "user_id CHAR(36) PRIMARY KEY, voice_name VARCHAR(100) NOT NULL, "
                    + "speech_rate_multiplier DECIMAL(3,2) NOT NULL DEFAULT 1.05, "
                    + "volume_multiplier DECIMAL(3,2) NOT NULL DEFAULT 1.00, "
                    + "preferred_verbosity VARCHAR(16) NOT NULL DEFAULT 'STANDARD', "
                    + "support_start_next_session BOOLEAN NOT NULL DEFAULT FALSE, "
                    + "recent_support_signal_count INT NOT NULL DEFAULT 0, "
                    + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        }
    }

    private UserVoiceSettingsVo settings(String voiceName, String speechRateMultiplier, String volumeMultiplier) {
        UserVoiceSettingsVo settings = new UserVoiceSettingsVo();
        settings.setUserId(USER_ID.toString());
        settings.setVoiceName(voiceName);
        settings.setSpeechRateMultiplier(
                speechRateMultiplier == null ? null : new BigDecimal(speechRateMultiplier));
        settings.setVolumeMultiplier(volumeMultiplier == null ? null : new BigDecimal(volumeMultiplier));
        return settings;
    }
}
