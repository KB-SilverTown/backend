package com.silvertown.domain.voice.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.silvertown.domain.voice.vo.VoiceStreamTicketVo;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VoiceStreamTicketMapperIntegrationTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 9, 18, 0);
    private SqlSessionFactory sessionFactory;
    private ExecutorService executorService;

    @BeforeEach
    void setUp() throws Exception {
        PooledDataSource dataSource = new PooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa", "");
        Configuration configuration = new Configuration(
                new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(VoiceStreamTicketMapper.class);
        try (var mapperXml = Resources.getResourceAsReader("mapper/voice/VoiceStreamTicketMapper.xml")) {
            new org.apache.ibatis.builder.xml.XMLMapperBuilder(
                    mapperXml, configuration, "mapper/voice/VoiceStreamTicketMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        sessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        executorService = Executors.newFixedThreadPool(2);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE voice_stream_ticket (
                        ticket_id CHAR(36) NOT NULL PRIMARY KEY,
                        ticket_hash CHAR(64) NOT NULL UNIQUE,
                        user_id CHAR(36) NOT NULL,
                        session_id CHAR(36) NOT NULL,
                        expires_at TIMESTAMP NOT NULL,
                        used_at TIMESTAMP NULL,
                        created_at TIMESTAMP NOT NULL
                    )
                    """);
        }
    }

    @AfterEach
    void tearDown() {
        executorService.shutdownNow();
    }

    @Test
    void atomicallyConsumesTicketForOnlyOneOfTwoConcurrentHandshakeAttempts() throws Exception {
        VoiceStreamTicketVo ticket = newTicket();
        try (SqlSession sqlSession = sessionFactory.openSession(true)) {
            sqlSession.getMapper(VoiceStreamTicketMapper.class).insert(ticket);
        }
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> consume = () -> {
            ready.countDown();
            start.await();
            try (SqlSession sqlSession = sessionFactory.openSession(true)) {
                return sqlSession.getMapper(VoiceStreamTicketMapper.class).consumeIfUnusedAndUnexpired(
                        ticket.getTicketId(), NOW, NOW);
            }
        };

        Future<Integer> first = executorService.submit(consume);
        Future<Integer> second = executorService.submit(consume);
        ready.await();
        start.countDown();

        assertEquals(1, first.get() + second.get());
        try (SqlSession sqlSession = sessionFactory.openSession()) {
            VoiceStreamTicketVo consumed = sqlSession.getMapper(VoiceStreamTicketMapper.class)
                    .findUnusedUnexpiredByHash(ticket.getTicketHash(), NOW);
            assertNull(consumed);
        }
    }

    @Test
    void doesNotFindExpiredTicketForHandshakeConsumption() {
        VoiceStreamTicketVo ticket = newTicket();
        ticket.setExpiresAt(NOW.minusSeconds(1));
        try (SqlSession sqlSession = sessionFactory.openSession(true)) {
            VoiceStreamTicketMapper mapper = sqlSession.getMapper(VoiceStreamTicketMapper.class);
            mapper.insert(ticket);
            assertNull(mapper.findUnusedUnexpiredByHash(ticket.getTicketHash(), NOW));
        }
    }

    private VoiceStreamTicketVo newTicket() {
        VoiceStreamTicketVo ticket = new VoiceStreamTicketVo();
        ticket.setTicketId("30000000-0000-0000-0000-000000000001");
        ticket.setTicketHash("a".repeat(64));
        ticket.setUserId("00000000-0000-0000-0000-000000000001");
        ticket.setSessionId("10000000-0000-0000-0000-000000000001");
        ticket.setExpiresAt(NOW.plusSeconds(60));
        ticket.setCreatedAt(NOW);
        return ticket;
    }
}
