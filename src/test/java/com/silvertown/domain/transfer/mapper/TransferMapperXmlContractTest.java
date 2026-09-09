package com.silvertown.domain.transfer.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class TransferMapperXmlContractTest {
    @Test
    void cancellationLookupLocksTransferUntilStateTransitionCompletes() throws Exception {
        String xml = readXml();
        int queryStart = xml.indexOf("<select id=\"findOwnedByIdForUpdate\"");
        int queryEnd = xml.indexOf("</select>", queryStart);
        assertTrue(queryStart >= 0 && queryEnd > queryStart);
        String lockingQuery = xml.substring(queryStart, queryEnd).toUpperCase(Locale.ROOT);
        assertTrue(lockingQuery.contains("FOR UPDATE"));
        assertFalse(lockingQuery.contains("SELECT *"));
    }

    @Test
    void voiceSessionOwnershipLookupLocksTheOwnedSession() throws Exception {
        String xml = readXml();
        int queryStart = xml.indexOf("<select id=\"findOwnedVoiceSessionIdForUpdate\"");
        int queryEnd = xml.indexOf("</select>", queryStart);
        assertTrue(queryStart >= 0 && queryEnd > queryStart);
        String lockingQuery = xml.substring(queryStart, queryEnd).toUpperCase(Locale.ROOT);
        assertTrue(lockingQuery.contains("USER_ID = #{USERID"));
        assertTrue(lockingQuery.contains("SESSION_ID = #{SESSIONID"));
        assertTrue(lockingQuery.contains("FOR UPDATE"));
    }

    @Test
    void idempotencyTransactionQueryMapsTheOwnerUserId() throws Exception {
        String xml = readXml();
        int mapStart = xml.indexOf("<resultMap id=\"transactionMap\"");
        int mapEnd = xml.indexOf("</resultMap>", mapStart);
        int queryStart = xml.indexOf("<select id=\"findTransactionByIdempotencyKey\"");
        int queryEnd = xml.indexOf("</select>", queryStart);
        assertTrue(mapStart >= 0 && mapEnd > mapStart);
        assertTrue(queryStart >= 0 && queryEnd > queryStart);
        assertTrue(xml.substring(mapStart, mapEnd).contains("property=\"userId\" column=\"user_id\""));
        String query = xml.substring(queryStart, queryEnd);
        String projection = query.substring(query.indexOf("SELECT") + "SELECT".length(), query.indexOf("FROM"));
        assertTrue(projection.contains("tr.user_id"));
    }

    @Test
    void confirmationUpdateAcceptsGuardianVerificationReconfirmationState() throws Exception {
        String xml = readXml();
        int queryStart = xml.indexOf("<update id=\"confirmIfRiskChecked\"");
        int queryEnd = xml.indexOf("</update>", queryStart);

        assertTrue(queryStart >= 0 && queryEnd > queryStart);
        String update = xml.substring(queryStart, queryEnd);
        int whereStart = update.indexOf("WHERE");

        assertTrue(whereStart >= 0);
        assertTrue(update.substring(whereStart).contains("'RECONFIRM'"));
        assertTrue(update.contains("confirmation_token_hash"));
        assertTrue(update.contains("confirmation_token_expires_at"));
    }

    @Test
    void transferQueriesLoadTheStoredConfirmationTokenMetadata() throws Exception {
        String xml = readXml();

        assertTrue(xml.contains("property=\"confirmationTokenHash\" column=\"confirmation_token_hash\""));
        assertTrue(xml.contains("property=\"confirmationTokenExpiresAt\" column=\"confirmation_token_expires_at\""));
        assertTrue(statement(xml, "update", "refreshConfirmationToken")
                .contains("status = 'CONFIRMED'"));
    }

    @Test
    void pinAndAuthenticationQueriesFollowTheCurrentDatabaseSchema() throws Exception {
        String xml = readXml();

        assertTrue(xml.contains("failed_attempt_count"));
        assertFalse(xml.contains("failed_attempts"));
        assertTrue(xml.contains("property=\"transferAuthenticationId\" column=\"transfer_authentication_id\""));
        assertTrue(statement(xml, "update", "expireAuthenticatedAuthentications")
                .contains("status = 'AUTHENTICATED'"));
        assertTrue(statement(xml, "update", "consumeAuthentication")
                .contains("status = 'AUTHENTICATED'"));
    }

    private String statement(String xml, String tag, String id) {
        int statementStart = xml.indexOf("<" + tag + " id=\"" + id + "\"");
        int statementEnd = xml.indexOf("</" + tag + ">", statementStart);

        assertTrue(statementStart >= 0 && statementEnd > statementStart);
        return xml.substring(statementStart, statementEnd);
    }

    private String readXml() throws Exception {
        try (var input = getClass().getClassLoader()
                .getResourceAsStream("mapper/transfer/TransferMapper.xml")) {
            if (input == null) throw new AssertionError("TransferMapper.xml을 찾을 수 없습니다.");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
