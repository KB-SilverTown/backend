package com.silvertown.domain.voice.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.silvertown.domain.voice.vo.DialogueTurnVo;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DialogueTurnMapperIntegrationTest {
    private static final String SESSION_ID = "10000000-0000-0000-0000-000000000001";
    private static final String USER_TURN_ID = "20000000-0000-0000-0000-000000000001";
    private static final String AI_TURN_ID = "30000000-0000-0000-0000-000000000001";
    private SqlSessionFactory sessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        PooledDataSource dataSource = new PooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa", "");
        Configuration configuration = new Configuration(
                new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(DialogueTurnMapper.class);
        try (var mapperXml = Resources.getResourceAsReader("mapper/voice/DialogueTurnMapper.xml")) {
            new org.apache.ibatis.builder.xml.XMLMapperBuilder(
                    mapperXml, configuration, "mapper/voice/DialogueTurnMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        sessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        createSchema(dataSource);
    }

    @Test
    void persistsUserAndAiTurnsAndFindsAnExistingTurnInItsSession() {
        try (SqlSession sqlSession = sessionFactory.openSession()) {
            DialogueTurnMapper mapper = sqlSession.getMapper(DialogueTurnMapper.class);
            mapper.insert(turn(USER_TURN_ID, 1, "USER"));
            mapper.insert(turn(AI_TURN_ID, 2, "AI"));

            DialogueTurnVo found = mapper.findBySessionIdAndTurnId(SESSION_ID, USER_TURN_ID);
            assertEquals(USER_TURN_ID, found.getTurnId());
            assertEquals("USER", found.getSpeaker());
            assertEquals("김철수에게 오만원 보내줘", found.getTranscript());
            assertEquals(3, mapper.findNextSequenceNo(SESSION_ID));
        }
    }

    @Test
    void exposesTheDatabaseSequenceConstraintToTheServiceLayer() {
        try (SqlSession sqlSession = sessionFactory.openSession()) {
            DialogueTurnMapper mapper = sqlSession.getMapper(DialogueTurnMapper.class);
            mapper.insert(turn(USER_TURN_ID, 1, "USER"));

            assertThrows(PersistenceException.class,
                    () -> mapper.insert(turn(AI_TURN_ID, 1, "AI")));
        }
    }

    private void createSchema(PooledDataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE dialogue_turns ("
                    + "turn_id CHAR(36) PRIMARY KEY, session_id CHAR(36) NOT NULL, sequence_no INT NOT NULL, "
                    + "speaker VARCHAR(10) NOT NULL, transcript TEXT NULL, tts_text TEXT NULL, "
                    + "tts_ssml TEXT NULL, display_card JSON NULL, step VARCHAR(30) NULL, intent VARCHAR(50) NULL, "
                    + "extracted_slots JSON NULL, silence_ms INT NOT NULL DEFAULT 0, "
                    + "replay_count INT NOT NULL DEFAULT 0, interrupted BOOLEAN NOT NULL DEFAULT FALSE, "
                    + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "stt_confidence DECIMAL(5, 4) NULL, input_type VARCHAR(20) NULL, "
                    + "CONSTRAINT uk_dialogue_turns_session_sequence UNIQUE (session_id, sequence_no))");
        }
    }

    private DialogueTurnVo turn(String turnId, int sequenceNo, String speaker) {
        DialogueTurnVo turn = new DialogueTurnVo();
        turn.setTurnId(turnId);
        turn.setSessionId(SESSION_ID);
        turn.setSequenceNo(sequenceNo);
        turn.setSpeaker(speaker);
        turn.setTranscript("USER".equals(speaker) ? "김철수에게 오만원 보내줘" : null);
        turn.setTtsText("AI".equals(speaker) ? "송금 정보를 확인하겠습니다." : null);
        turn.setStep("AWAITING_INPUT");
        turn.setSilenceMs(0);
        turn.setReplayCount(0);
        turn.setInterrupted(false);
        turn.setSttConfidence(new BigDecimal("0.94"));
        turn.setInputType("VOICE");
        return turn;
    }
}
