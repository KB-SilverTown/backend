package com.silvertown.domain.reminder.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.silvertown.domain.reminder.dto.ReminderCreateRequest;
import com.silvertown.domain.reminder.mapper.ReminderMapper;
import com.silvertown.domain.reminder.service.ReminderService;
import com.silvertown.domain.reminder.service.impl.ReminderServiceImpl;
import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import com.silvertown.global.common.exception.GlobalExceptionHandler;
import com.silvertown.global.common.filter.RequestIdFilter;
import com.silvertown.global.security.AuthenticatedUserId;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ReminderDuplicateResponseIntegrationTest {
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-03T01:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final String REQUEST_JSON =
            "{\"title\":\"전기요금 납부\",\"scheduledAt\":\"2026-09-10T09:00:00+09:00\"}";

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private PooledDataSource dataSource;
    private ReminderService reminderService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new PooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL",
                "sa",
                "");
        SqlSessionFactory sqlSessionFactory = sqlSessionFactory(dataSource);
        createSchema();

        ReminderMapper reminderMapper = new SqlSessionTemplate(sqlSessionFactory).getMapper(ReminderMapper.class);
        reminderService = new ReminderServiceImpl(reminderMapper, CLOCK);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ReminderController(reminderService, new AuthenticatedUserId()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @AfterEach
    void tearDown() {
        dataSource.forceCloseAll();
    }

    @Test
    void concurrentServiceCreatesLeaveOneRowAndConvertTheOtherToDuplicateError() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<CreationOutcome>> results = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                results.add(executor.submit(() -> createAtTheSameTime(ready, start)));
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            int created = 0;
            int duplicates = 0;
            for (Future<CreationOutcome> result : results) {
                if (result.get(5, TimeUnit.SECONDS) == CreationOutcome.CREATED) {
                    created++;
                } else {
                    duplicates++;
                }
            }

            assertEquals(1, created);
            assertEquals(1, duplicates);
            assertEquals(1, reminderCount());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void returnsConflictWithReminderDuplicateCodeForTheSecondCreateRequest() throws Exception {
        mockMvc.perform(post("/api/reminders")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_JSON))
                .andExpect(status().isCreated());

        JsonNode error = objectMapper.readTree(mockMvc.perform(post("/api/reminders")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_JSON))
                .andExpect(status().isConflict())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());

        assertEquals("REMINDER_DUPLICATE", error.path("code").asText());
        assertEquals(1, reminderCount());
    }

    private CreationOutcome createAtTheSameTime(CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS));
        try {
            reminderService.create(USER_ID, request());
            return CreationOutcome.CREATED;
        } catch (BusinessException exception) {
            assertEquals(ErrorCode.REMINDER_DUPLICATE, exception.getErrorCode());
            return CreationOutcome.DUPLICATE;
        }
    }

    private SqlSessionFactory sqlSessionFactory(PooledDataSource source) throws Exception {
        Configuration configuration = new Configuration(
                new Environment("test", new JdbcTransactionFactory(), source));
        configuration.addMapper(ReminderMapper.class);
        try (var mapperXml = Resources.getResourceAsReader("mapper/reminder/ReminderMapper.xml")) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    "mapper/reminder/ReminderMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private void createSchema() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE reminders ("
                    + "reminder_id CHAR(36) PRIMARY KEY, user_id CHAR(36) NOT NULL, bill_id CHAR(36), "
                    + "bill_id_duplicate_key CHAR(36) GENERATED ALWAYS AS (COALESCE(bill_id, '')), "
                    + "title VARCHAR(200) NOT NULL, remind_at TIMESTAMP NOT NULL, status VARCHAR(20) NOT NULL, "
                    + "CONSTRAINT uk_reminders_user_bill_title_remind_at "
                    + "UNIQUE (user_id, bill_id_duplicate_key, title, remind_at))");
        }
    }

    private int reminderCount() throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM reminders")) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private ReminderCreateRequest request() throws Exception {
        return objectMapper.readValue(REQUEST_JSON, ReminderCreateRequest.class);
    }

    private UsernamePasswordAuthenticationToken authentication() {
        return UsernamePasswordAuthenticationToken.authenticated(USER_ID.toString(), null, List.of());
    }

    private enum CreationOutcome {
        CREATED,
        DUPLICATE
    }
}
