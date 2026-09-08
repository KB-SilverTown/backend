package com.silvertown.domain.risk.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RiskScoreMapperXmlContractTest {
    @Test
    void historyQueriesUseOnlySuccessfulTransactionsAndBoundTheirRows() throws Exception {
        String xml = mapperXml();

        assertTrue(xml.contains("tx.status = 'SUCCESS'"));
        assertTrue(xml.contains("LIMIT 100"));
        assertTrue(xml.contains("LIMIT 50"));
        assertTrue(xml.contains("INTERVAL 180 DAY"));
        assertTrue(xml.contains("INTERVAL 7 DAY"));
        assertFalse(xml.toUpperCase().contains("SELECT *"));
    }

    @Test
    void holdTransitionDoesNotOverwriteTerminalTransferStates() throws Exception {
        String xml = mapperXml();
        int queryStart = xml.indexOf("<update id=\"holdTransfer\"");
        int queryEnd = xml.indexOf("</update>", queryStart);

        assertTrue(queryStart >= 0 && queryEnd > queryStart);
        String holdQuery = xml.substring(queryStart, queryEnd);
        assertTrue(holdQuery.contains("status IN ('DRAFT', 'RECONFIRM', 'CONFIRMED', 'HELD')"));
        assertFalse(holdQuery.contains("'EXECUTED'"));
        assertFalse(holdQuery.contains("'CANCELLED'"));
    }

    private String mapperXml() throws Exception {
        try (var input = getClass().getClassLoader()
                .getResourceAsStream("mapper/risk/RiskScoreMapper.xml")) {
            if (input == null) throw new AssertionError("RiskScoreMapper.xml을 찾을 수 없습니다.");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
