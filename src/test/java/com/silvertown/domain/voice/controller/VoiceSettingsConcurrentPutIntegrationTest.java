package com.silvertown.domain.voice.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.silvertown.domain.voice.mapper.UserVoiceSettingsMapper;
import com.silvertown.domain.voice.service.impl.VoiceSettingsServiceImpl;
import com.silvertown.domain.voice.vo.UserVoiceSettingsVo;
import com.silvertown.global.common.exception.GlobalExceptionHandler;
import com.silvertown.global.common.filter.RequestIdFilter;
import com.silvertown.global.security.AuthenticatedUserId;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class VoiceSettingsConcurrentPutIntegrationTest {
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private SqlSessionFactory sessionFactory;
    private BarrierUserVoiceSettingsMapper userVoiceSettingsMapper;
    private MockMvc mockMvc;

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

        userVoiceSettingsMapper = new BarrierUserVoiceSettingsMapper(sessionFactory);
        VoiceSettingsServiceImpl service = new VoiceSettingsServiceImpl(
                userVoiceSettingsMapper, Clock.system(ZoneId.of("Asia/Seoul")));
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new VoiceSettingsController(service, new AuthenticatedUserId()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void concurrentPartialPutRequestsPreserveBothRequestedSettings() throws Exception {
        try (SqlSession session = sessionFactory.openSession(true)) {
            session.getMapper(UserVoiceSettingsMapper.class).upsert(settings(
                    "ko-KR-JiMinNeural", "1.05", "1.00"));
        }

        userVoiceSettingsMapper.enablePairedReadBarrier();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var speechRateUpdate = executor.submit((Callable<Void>) () -> {
                performUpdate("{\"speechRateMultiplier\":1.20}");
                return null;
            });
            var volumeUpdate = executor.submit((Callable<Void>) () -> {
                performUpdate("{\"volumeMultiplier\":1.20}");
                return null;
            });

            speechRateUpdate.get(5, TimeUnit.SECONDS);
            volumeUpdate.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        try (SqlSession session = sessionFactory.openSession()) {
            UserVoiceSettingsVo found = session.getMapper(UserVoiceSettingsMapper.class)
                    .findByUserId(USER_ID.toString());
            assertEquals("ko-KR-JiMinNeural", found.getVoiceName());
            assertEquals(new BigDecimal("1.20"), found.getSpeechRateMultiplier());
            assertEquals(new BigDecimal("1.20"), found.getVolumeMultiplier());
        }
    }

    @Test
    void putUpdateRefreshesUpdatedAt() throws Exception {
        LocalDateTime previousUpdatedAt = LocalDateTime.of(2000, 1, 1, 0, 0);
        try (SqlSession session = sessionFactory.openSession(true)) {
            session.getMapper(UserVoiceSettingsMapper.class).upsert(settings(
                    "ko-KR-JiMinNeural", "1.05", "1.00"));
            try (PreparedStatement statement = session.getConnection().prepareStatement(
                    "UPDATE user_voice_settings SET updated_at = ? WHERE user_id = ?")) {
                statement.setTimestamp(1, Timestamp.valueOf(previousUpdatedAt));
                statement.setString(2, USER_ID.toString());
                statement.executeUpdate();
            }
        }

        JsonNode response = objectMapper.readTree(performUpdate("{\"speechRateMultiplier\":1.20}"));

        assertEquals(1.20, response.get("speechRateMultiplier").asDouble());
        assertTrue(OffsetDateTime.parse(response.get("updatedAt").asText())
                .toLocalDateTime().isAfter(previousUpdatedAt));
    }

    private String performUpdate(String requestBody) throws Exception {
        return mockMvc.perform(put("/api/users/me/voice-settings")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private UsernamePasswordAuthenticationToken authentication() {
        return UsernamePasswordAuthenticationToken.authenticated(USER_ID.toString(), null, List.of());
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
        settings.setSpeechRateMultiplier(new BigDecimal(speechRateMultiplier));
        settings.setVolumeMultiplier(new BigDecimal(volumeMultiplier));
        return settings;
    }

    private static class BarrierUserVoiceSettingsMapper implements UserVoiceSettingsMapper {
        private final SqlSessionFactory sessionFactory;
        private final CyclicBarrier firstTwoFindsBarrier = new CyclicBarrier(2);
        private final AtomicBoolean pairedReadBarrierEnabled = new AtomicBoolean();
        private final AtomicInteger findCalls = new AtomicInteger();

        private BarrierUserVoiceSettingsMapper(SqlSessionFactory sessionFactory) {
            this.sessionFactory = sessionFactory;
        }

        private void enablePairedReadBarrier() {
            pairedReadBarrierEnabled.set(true);
        }

        @Override
        public UserVoiceSettingsVo findByUserId(String userId) {
            UserVoiceSettingsVo settings;
            try (SqlSession session = sessionFactory.openSession(true)) {
                settings = session.getMapper(UserVoiceSettingsMapper.class).findByUserId(userId);
            }

            if (pairedReadBarrierEnabled.get() && findCalls.incrementAndGet() <= 2) {
                awaitPairedFind();
            }
            return settings;
        }

        @Override
        public int upsert(UserVoiceSettingsVo userVoiceSettings) {
            try (SqlSession session = sessionFactory.openSession(true)) {
                return session.getMapper(UserVoiceSettingsMapper.class).upsert(userVoiceSettings);
            }
        }

        private void awaitPairedFind() {
            try {
                firstTwoFindsBarrier.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Concurrent request test was interrupted.", exception);
            } catch (BrokenBarrierException | TimeoutException exception) {
                throw new AssertionError("Concurrent requests did not reach the paired read.", exception);
            }
        }
    }
}
