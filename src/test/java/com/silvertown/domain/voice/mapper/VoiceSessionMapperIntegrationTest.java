package com.silvertown.domain.voice.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.silvertown.domain.voice.vo.VoiceSessionVo;
import java.sql.Connection;
import java.sql.Statement;
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

class VoiceSessionMapperIntegrationTest {
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID SESSION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final LocalDateTime STREAM_NOW = LocalDateTime.of(2026, 9, 2, 10, 0);
    private SqlSessionFactory sessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        PooledDataSource dataSource = new PooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa", "");
        Configuration configuration = new Configuration(
                new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(VoiceSessionMapper.class);
        try (var mapperXml = Resources.getResourceAsReader("mapper/voice/VoiceSessionMapper.xml")) {
            new org.apache.ibatis.builder.xml.XMLMapperBuilder(
                    mapperXml, configuration, "mapper/voice/VoiceSessionMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        sessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        createSchema(dataSource);
    }

    @Test
    void persistsOwnedSessionAndClosesItOnlyOnce() {
        try (SqlSession sqlSession = sessionFactory.openSession()) {
            VoiceSessionMapper mapper = sqlSession.getMapper(VoiceSessionMapper.class);
            mapper.insert(newSession());

            VoiceSessionVo found = mapper.findOwnedById(OWNER_ID.toString(), SESSION_ID.toString());
            assertEquals("LISTENING", found.getStatus());
            assertEquals("GENERAL_FINANCE", found.getFlowType());
            assertEquals("CLIENT", found.getSttMode());
            assertEquals("GENERAL_FINANCE", found.getEntryPoint());
            assertNull(mapper.findOwnedById(OTHER_ID.toString(), SESSION_ID.toString()));

            assertEquals(1, mapper.closeOwned(
                    OWNER_ID.toString(), SESSION_ID.toString(), "AWAITING_INPUT",
                    LocalDateTime.of(2026, 9, 2, 10, 1)));
            assertEquals(0, mapper.closeOwned(
                    OWNER_ID.toString(), SESSION_ID.toString(), "AWAITING_INPUT",
                    LocalDateTime.of(2026, 9, 2, 10, 2)));
            assertEquals("CLOSED", mapper.findOwnedById(
                    OWNER_ID.toString(), SESSION_ID.toString()).getStatus());
        }
    }

    @Test
    void claimsOnlyOneUnexpiredActiveTurnAndCompletesIt() {
        try (SqlSession sqlSession = sessionFactory.openSession()) {
            VoiceSessionMapper mapper = sqlSession.getMapper(VoiceSessionMapper.class);
            mapper.insert(newSession());

            VoiceSessionVo locked = mapper.findOwnedByIdForUpdate(
                    OWNER_ID.toString(), SESSION_ID.toString());
            assertEquals(SESSION_ID.toString(), locked.getSessionId());
            assertEquals(1, mapper.claimForTurn(
                    OWNER_ID.toString(), SESSION_ID.toString(), LocalDateTime.of(2026, 9, 2, 10, 1)));
            assertEquals(0, mapper.claimForTurn(
                    OWNER_ID.toString(), SESSION_ID.toString(), LocalDateTime.of(2026, 9, 2, 10, 1)));
            assertEquals("PROCESSING", mapper.findOwnedById(
                    OWNER_ID.toString(), SESSION_ID.toString()).getStatus());

            assertEquals(1, mapper.restoreTurnClaim(
                    OWNER_ID.toString(), SESSION_ID.toString(), "LISTENING"));
            assertEquals("LISTENING", mapper.findOwnedById(
                    OWNER_ID.toString(), SESSION_ID.toString()).getStatus());
            assertEquals(1, mapper.claimForTurn(
                    OWNER_ID.toString(), SESSION_ID.toString(), LocalDateTime.of(2026, 9, 2, 10, 1)));

            assertEquals(1, mapper.completeTurn(
                    OWNER_ID.toString(), SESSION_ID.toString(), "AWAITING_INPUT"));
            assertEquals(0, mapper.completeTurn(
                    OWNER_ID.toString(), SESSION_ID.toString(), "AWAITING_INPUT"));
            assertEquals("SPEAKING", mapper.findOwnedById(
                    OWNER_ID.toString(), SESSION_ID.toString()).getStatus());
        }
    }

    @Test
    void doesNotClaimExpiredOrOtherUsersSession() {
        try (SqlSession sqlSession = sessionFactory.openSession()) {
            VoiceSessionMapper mapper = sqlSession.getMapper(VoiceSessionMapper.class);
            VoiceSessionVo expiredSession = newSession();
            expiredSession.setExpiresAt(LocalDateTime.of(2026, 9, 2, 9, 59));
            mapper.insert(expiredSession);

            assertEquals(0, mapper.claimForTurn(
                    OWNER_ID.toString(), SESSION_ID.toString(), LocalDateTime.of(2026, 9, 2, 10, 0)));
            assertEquals(0, mapper.claimForTurn(
                    OTHER_ID.toString(), SESSION_ID.toString(), LocalDateTime.of(2026, 9, 2, 10, 0)));
            assertEquals("LISTENING", mapper.findOwnedById(
                    OWNER_ID.toString(), SESSION_ID.toString()).getStatus());
        }
    }

    @Test
    void doesNotClaimExpiredStreamInputTurn() {
        try (SqlSession sqlSession = sessionFactory.openSession()) {
            VoiceSessionMapper mapper = sqlSession.getMapper(VoiceSessionMapper.class);
            VoiceSessionVo expiredSession = newSession();
            expiredSession.setFlowType("TRANSFER");
            expiredSession.setSttMode("BACKEND_STREAM");
            expiredSession.setEntryPoint("TRANSFER");
            expiredSession.setExpiresAt(STREAM_NOW.minusSeconds(1));
            mapper.insert(expiredSession);

            assertEquals(0, mapper.claimStreamInputTurn(
                    OWNER_ID.toString(),
                    SESSION_ID.toString(),
                    "20000000-0000-0000-0000-000000000001",
                    STREAM_NOW));
        }
    }

    @Test
    void persistsStreamLifecycleStateTransitions() {
        String inputTurnId = "20000000-0000-0000-0000-000000000001";
        String aiTurnId = "30000000-0000-0000-0000-000000000001";
        try (SqlSession sqlSession = sessionFactory.openSession()) {
            VoiceSessionMapper mapper = sqlSession.getMapper(VoiceSessionMapper.class);
            VoiceSessionVo session = newSession();
            session.setFlowType("TRANSFER");
            session.setSttMode("BACKEND_STREAM");
            session.setEntryPoint("TRANSFER");
            mapper.insert(session);

            assertEquals(1, mapper.claimStreamInputTurn(
                    OWNER_ID.toString(), SESSION_ID.toString(), inputTurnId, STREAM_NOW));
            assertEquals(1, mapper.beginStreamFinalProcessing(
                    OWNER_ID.toString(), SESSION_ID.toString(), inputTurnId, 0, STREAM_NOW));
            assertEquals(1, mapper.completeStreamTurnWithAi(
                    OWNER_ID.toString(), SESSION_ID.toString(), inputTurnId, 0, aiTurnId, STREAM_NOW));

            VoiceSessionVo speaking = mapper.findOwnedById(OWNER_ID.toString(), SESSION_ID.toString());
            assertEquals("SPEAKING", speaking.getStatus());
            assertNull(speaking.getActiveInputTurnId());
            assertEquals(aiTurnId, speaking.getActiveAiTurnId());
            assertEquals(0, speaking.getLifecycleGeneration());

            assertEquals(1, mapper.interruptActiveAiTurn(
                    OWNER_ID.toString(), SESSION_ID.toString(), aiTurnId, STREAM_NOW));
            VoiceSessionVo listening = mapper.findOwnedById(OWNER_ID.toString(), SESSION_ID.toString());
            assertEquals("LISTENING", listening.getStatus());
            assertNull(listening.getActiveAiTurnId());
            assertEquals(1, listening.getLifecycleGeneration());

            assertEquals(1, mapper.claimStreamInputTurn(
                    OWNER_ID.toString(), SESSION_ID.toString(), inputTurnId, STREAM_NOW));
            assertEquals(1, mapper.cancelActiveInputTurn(
                    OWNER_ID.toString(), SESSION_ID.toString(), inputTurnId, STREAM_NOW));
            VoiceSessionVo cancelledInput = mapper.findOwnedById(
                    OWNER_ID.toString(), SESSION_ID.toString());
            assertEquals("LISTENING", cancelledInput.getStatus());
            assertNull(cancelledInput.getActiveInputTurnId());
            assertEquals(2, cancelledInput.getLifecycleGeneration());
        }
    }

    private void createSchema(PooledDataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE voice_sessions ("
                    + "session_id CHAR(36) PRIMARY KEY, user_id CHAR(36) NOT NULL, status VARCHAR(20) NOT NULL, "
                    + "from_account_id CHAR(36) NULL, transfer_id CHAR(36) NULL, "
                    + "current_step VARCHAR(30) NOT NULL, flow_type VARCHAR(30), stt_mode VARCHAR(20), "
                    + "entry_point VARCHAR(30) NOT NULL, "
                    + "active_input_turn_id CHAR(36), active_ai_turn_id CHAR(36), "
                    + "lifecycle_generation BIGINT NOT NULL DEFAULT 0, "
                    + "started_at TIMESTAMP NOT NULL, ended_at TIMESTAMP NULL, expires_at TIMESTAMP NULL)");
        }
    }

    private VoiceSessionVo newSession() {
        VoiceSessionVo voiceSession = new VoiceSessionVo();
        voiceSession.setSessionId(SESSION_ID.toString());
        voiceSession.setUserId(OWNER_ID.toString());
        voiceSession.setStatus("LISTENING");
        voiceSession.setCurrentStep("AWAITING_INPUT");
        voiceSession.setFlowType("GENERAL_FINANCE");
        voiceSession.setSttMode("CLIENT");
        voiceSession.setEntryPoint("GENERAL_FINANCE");
        voiceSession.setStartedAt(LocalDateTime.of(2026, 9, 2, 10, 0));
        voiceSession.setExpiresAt(LocalDateTime.of(2026, 9, 2, 10, 15));
        return voiceSession;
    }
}
