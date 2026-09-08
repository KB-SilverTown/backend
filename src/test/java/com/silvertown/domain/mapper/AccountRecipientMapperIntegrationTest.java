package com.silvertown.domain.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.silvertown.domain.account.mapper.AccountMapper;
import com.silvertown.domain.account.vo.BankAccount;
import com.silvertown.domain.recipient.mapper.RecipientMapper;
import com.silvertown.domain.recipient.vo.Recipient;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
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

class AccountRecipientMapperIntegrationTest {
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
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
        configuration.addMapper(AccountMapper.class);
        configuration.addMapper(RecipientMapper.class);
        try (var accountXml = Resources.getResourceAsReader("mapper/account/AccountMapper.xml");
                var recipientXml = Resources.getResourceAsReader("mapper/recipient/RecipientMapper.xml")) {
            new org.apache.ibatis.builder.xml.XMLMapperBuilder(
                    accountXml, configuration, "mapper/account/AccountMapper.xml", configuration.getSqlFragments()).parse();
            new org.apache.ibatis.builder.xml.XMLMapperBuilder(
                    recipientXml, configuration, "mapper/recipient/RecipientMapper.xml", configuration.getSqlFragments()).parse();
        }
        sessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        createSchema(dataSource);
    }

    @Test
    void accountQueryReturnsOnlyAuthenticatedOwnersActiveAccounts() throws Exception {
        try (SqlSession session = sessionFactory.openSession()) {
            Connection connection = session.getConnection();
            insertAccount(connection, OWNER_ID, true, "내 활성 계좌");
            insertAccount(connection, OWNER_ID, false, "내 비활성 계좌");
            insertAccount(connection, OTHER_ID, true, "다른 사용자 계좌");
            session.commit();

            List<BankAccount> accounts = session.getMapper(AccountMapper.class)
                    .findActiveByUserId(OWNER_ID.toString());

            assertEquals(1, accounts.size());
            assertEquals("내 활성 계좌", accounts.get(0).getAccountName());
        }
    }

    @Test
    void recipientQueryEnforcesOwnerOrderAndTwentyRowLimit() throws Exception {
        try (SqlSession session = sessionFactory.openSession()) {
            Connection connection = session.getConnection();
            for (int i = 0; i < 8; i++) insertRecipient(connection, OWNER_ID, "김연락" + i, "CONTACT");
            for (int i = 0; i < 8; i++) insertRecipient(connection, OWNER_ID, "김이력" + i, "HISTORY");
            for (int i = 0; i < 8; i++) insertRecipient(connection, OWNER_ID, "김수동" + i, "MANUAL");
            insertRecipient(connection, OTHER_ID, "김다른사용자", "CONTACT");
            session.commit();

            List<Recipient> recipients = session.getMapper(RecipientMapper.class)
                    .findCandidates(OWNER_ID.toString(), "김", Collections.emptyList());

            assertEquals(20, recipients.size());
            assertTrue(recipients.stream().noneMatch(r -> "김다른사용자".equals(r.getDisplayName())));
            assertEquals(Collections.nCopies(8, "CONTACT"), sources(recipients.subList(0, 8)));
            assertEquals(Collections.nCopies(8, "HISTORY"), sources(recipients.subList(8, 16)));
            assertEquals(Collections.nCopies(4, "MANUAL"), sources(recipients.subList(16, 20)));
        }
    }

    private List<String> sources(List<Recipient> recipients) {
        return recipients.stream().map(Recipient::getSource).toList();
    }

    private void createSchema(PooledDataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE bank_accounts (account_id CHAR(36), user_id CHAR(36), bank_code VARCHAR(20), account_number_enc VARBINARY(512), account_name VARCHAR(100), balance BIGINT, account_type VARCHAR(30), is_active BOOLEAN, synced_at TIMESTAMP NULL, created_at TIMESTAMP)");
            statement.execute("CREATE TABLE recipients (recipient_id CHAR(36), owner_user_id CHAR(36), display_name VARCHAR(100), relationship VARCHAR(50), bank_code VARCHAR(20), account_number_enc VARBINARY(512), source VARCHAR(20), last_used_at TIMESTAMP NULL)");
        }
    }

    private void insertAccount(Connection connection, UUID userId, boolean active, String name) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO bank_accounts VALUES (?, ?, '004', ?, ?, 1000, 'DEPOSIT', ?, NULL, CURRENT_TIMESTAMP)")) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, userId.toString());
            statement.setBytes(3, new byte[] {1, 2, 3});
            statement.setString(4, name);
            statement.setBoolean(5, active);
            statement.executeUpdate();
        }
    }

    private void insertRecipient(Connection connection, UUID userId, String name, String source) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO recipients VALUES (?, ?, ?, NULL, '004', ?, ?, NULL)")) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, userId.toString());
            statement.setString(3, name);
            statement.setBytes(4, new byte[] {1, 2, 3});
            statement.setString(5, source);
            statement.executeUpdate();
        }
    }
}
