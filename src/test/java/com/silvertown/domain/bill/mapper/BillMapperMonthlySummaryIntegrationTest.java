package com.silvertown.domain.bill.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.silvertown.domain.bill.vo.BillMonthlyAggregateVo;
import com.silvertown.domain.bill.vo.BillVo;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;

class BillMapperMonthlySummaryIntegrationTest {
    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";

    private PooledDataSource dataSource;
    private BillMapper billMapper;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new PooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa", "");
        createBillsTable();
        billMapper = new SqlSessionTemplate(newSessionFactory()).getMapper(BillMapper.class);
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null) {
            dataSource.forceCloseAll();
        }
    }

    @Test
    void mapsMonthlyAggregateAndLimitsReturnedItems() throws Exception {
        insertBill("00000000-0000-0000-0000-000000000011", "PAID", 10_000L, "2026-09-01");
        insertBill("00000000-0000-0000-0000-000000000012", "DRAFT", 20_000L, "2026-09-02");
        insertBill("00000000-0000-0000-0000-000000000013", "CANCELLED", 30_000L, "2026-09-03");
        insertBill("00000000-0000-0000-0000-000000000014", "EXPIRED", 40_000L, "2026-09-04");
        insertBill("00000000-0000-0000-0000-000000000015", "DRAFT", 50_000L, "2026-10-01");

        BillMonthlyAggregateVo aggregate = billMapper.summarizeOwnedByDueDateRange(
                USER_ID, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        List<BillVo> items = billMapper.findOwnedByDueDateRange(
                USER_ID, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 1);

        assertEquals(30_000L, aggregate.getTotalAmount());
        assertEquals(10_000L, aggregate.getPaidAmount());
        assertEquals(2L, aggregate.getTotalCount());
        assertEquals(1L, aggregate.getPaidCount());
        assertEquals(1, items.size());
        assertEquals("00000000-0000-0000-0000-000000000011", items.get(0).getBillId());
    }

    private SqlSessionFactory newSessionFactory() throws Exception {
        Configuration configuration = new Configuration(
                new Environment("test", new SpringManagedTransactionFactory(), dataSource));
        configuration.addMapper(BillMapper.class);
        try (var mapperXml = Resources.getResourceAsReader("mapper/bill/BillMapper.xml")) {
            new org.apache.ibatis.builder.xml.XMLMapperBuilder(
                    mapperXml, configuration, "mapper/bill/BillMapper.xml", configuration.getSqlFragments()).parse();
        }
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private void createBillsTable() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE bills ("
                    + "bill_id CHAR(36) PRIMARY KEY, user_id CHAR(36) NOT NULL, payee VARCHAR(100), "
                    + "amount BIGINT, due_date DATE, payment_reference VARCHAR(100), "
                    + "ocr_confidence DECIMAL(5,4), field_confidences CLOB, status VARCHAR(20) NOT NULL, "
                    + "confirmation_token_hash CHAR(64), confirmation_token_expires_at TIMESTAMP, "
                    + "confirmed_at TIMESTAMP, created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL)");
        }
    }

    private void insertBill(String billId, String status, long amount, String dueDate) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO bills (bill_id, user_id, payee, amount, due_date, status, created_at, updated_at) "
                    + "VALUES ('" + billId + "', '" + USER_ID + "', '한국전력', " + amount + ", DATE '"
                    + dueDate + "', '" + status + "', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        }
    }
}
