package com.silvertown.domain.reminder.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.silvertown.domain.reminder.vo.ReminderVo;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

class ReminderMapperIntegrationTest {
    private static final String OWNER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String OTHER_ID = "00000000-0000-0000-0000-000000000002";
    private static final String BILL_ID = "10000000-0000-0000-0000-000000000001";
    private SqlSessionFactory sessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        PooledDataSource dataSource = new PooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        Configuration configuration = new Configuration(
                new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(ReminderMapper.class);
        try (var mapperXml = Resources.getResourceAsReader("mapper/reminder/ReminderMapper.xml")) {
            new org.apache.ibatis.builder.xml.XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    "mapper/reminder/ReminderMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        sessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        createSchema(dataSource);
    }

    @Test
    void filtersOwnedRemindersAndPersistsNewReminder() {
        try (SqlSession sqlSession = sessionFactory.openSession()) {
            ReminderMapper mapper = sqlSession.getMapper(ReminderMapper.class);
            mapper.insert(reminder(
                    "20000000-0000-0000-0000-000000000002",
                    OWNER_ID,
                    null,
                    "늦은 일정",
                    LocalDateTime.of(2026, 9, 10, 9, 0),
                    "SCHEDULED"));
            mapper.insert(reminder(
                    "20000000-0000-0000-0000-000000000003",
                    OWNER_ID,
                    null,
                    "완료 일정",
                    LocalDateTime.of(2026, 9, 8, 9, 0),
                    "SENT"));
            mapper.insert(reminder(
                    "20000000-0000-0000-0000-000000000004",
                    OTHER_ID,
                    null,
                    "다른 사용자 일정",
                    LocalDateTime.of(2026, 9, 7, 9, 0),
                    "SCHEDULED"));
            sqlSession.commit();

            List<ReminderVo> found = mapper.findOwnedByCondition(
                    OWNER_ID,
                    "SCHEDULED",
                    LocalDateTime.of(2026, 9, 1, 0, 0),
                    LocalDateTime.of(2026, 9, 30, 23, 59));

            assertEquals(1, found.size());
            assertEquals("늦은 일정", found.get(0).getTitle());
            assertEquals(1, mapper.existsOwnedBill(OWNER_ID, BILL_ID));
        }
    }

    @Test
    void preventsDuplicateReminderWithNullBillId() {
        LocalDateTime remindAt = LocalDateTime.of(2026, 9, 10, 9, 0);
        try (SqlSession sqlSession = sessionFactory.openSession()) {
            ReminderMapper mapper = sqlSession.getMapper(ReminderMapper.class);
            mapper.insert(reminder(
                    "20000000-0000-0000-0000-000000000005",
                    OWNER_ID,
                    null,
                    "중복 일정",
                    remindAt,
                    "SCHEDULED"));
            sqlSession.commit();

            assertThrows(PersistenceException.class, () -> mapper.insert(reminder(
                    "20000000-0000-0000-0000-000000000006",
                    OWNER_ID,
                    null,
                    "중복 일정",
                    remindAt,
                    "SCHEDULED")));
        }
    }

    @Test
    void updatesAndCancelsOnlyOwnedScheduledReminder() {
        String reminderId = "20000000-0000-0000-0000-000000000007";
        try (SqlSession sqlSession = sessionFactory.openSession()) {
            ReminderMapper mapper = sqlSession.getMapper(ReminderMapper.class);
            mapper.insert(reminder(
                    reminderId,
                    OWNER_ID,
                    null,
                    "변경 전 일정",
                    LocalDateTime.of(2026, 9, 10, 9, 0),
                    "SCHEDULED"));
            sqlSession.commit();

            ReminderVo owned = mapper.findOwnedById(OWNER_ID, reminderId);
            owned.setTitle("변경 후 일정");
            owned.setRemindAt(LocalDateTime.of(2026, 9, 11, 9, 0));
            assertEquals(1, mapper.updateScheduledForOwner(owned));
            assertEquals("변경 후 일정", mapper.findOwnedById(OWNER_ID, reminderId).getTitle());
            assertEquals(1, mapper.cancelScheduledForOwner(OWNER_ID, reminderId));
            assertEquals("CANCELLED", mapper.findOwnedById(OWNER_ID, reminderId).getStatus());
            assertEquals(0, mapper.cancelScheduledForOwner(OTHER_ID, reminderId));
        }
    }

    @Test
    void allowsOnlyOneConcurrentDuplicateInsert() throws Exception {
        LocalDateTime remindAt = LocalDateTime.of(2026, 9, 10, 9, 0);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                results.add(executor.submit(() -> insertConcurrentReminder(ready, start, remindAt)));
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            int successCount = 0;
            for (Future<Boolean> result : results) {
                if (result.get(5, TimeUnit.SECONDS)) {
                    successCount++;
                }
            }
            assertEquals(1, successCount);
        } finally {
            executor.shutdownNow();
        }
    }

    private void createSchema(PooledDataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE bills (bill_id CHAR(36) PRIMARY KEY, user_id CHAR(36) NOT NULL)");
            statement.execute("CREATE TABLE reminders ("
                    + "reminder_id CHAR(36) PRIMARY KEY, user_id CHAR(36) NOT NULL, bill_id CHAR(36), "
                    + "bill_id_duplicate_key CHAR(36) GENERATED ALWAYS AS (COALESCE(bill_id, '')), "
                    + "title VARCHAR(200) NOT NULL, remind_at TIMESTAMP NOT NULL, status VARCHAR(20) NOT NULL, "
                    + "CONSTRAINT uk_reminders_user_bill_title_remind_at "
                    + "UNIQUE (user_id, bill_id_duplicate_key, title, remind_at))");
            statement.execute("INSERT INTO bills (bill_id, user_id) VALUES ('" + BILL_ID + "', '" + OWNER_ID + "')");
        }
    }

    private boolean insertConcurrentReminder(
            CountDownLatch ready, CountDownLatch start, LocalDateTime remindAt) {
        try (SqlSession sqlSession = sessionFactory.openSession(true)) {
            ReminderMapper mapper = sqlSession.getMapper(ReminderMapper.class);
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            mapper.insert(reminder(
                    UUID.randomUUID().toString(),
                    OWNER_ID,
                    null,
                    "동시 요청 일정",
                    remindAt,
                    "SCHEDULED"));
            return true;
        } catch (PersistenceException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private ReminderVo reminder(
            String reminderId,
            String userId,
            String billId,
            String title,
            LocalDateTime remindAt,
            String status) {
        ReminderVo reminder = new ReminderVo();
        reminder.setReminderId(reminderId);
        reminder.setUserId(userId);
        reminder.setBillId(billId);
        reminder.setTitle(title);
        reminder.setRemindAt(remindAt);
        reminder.setStatus(status);
        return reminder;
    }
}
