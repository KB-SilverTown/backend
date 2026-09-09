package com.silvertown.domain.bill.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
class BillMapperXmlContractTest {
    @Test
    void billMapperXmlDeclaresOcrConfirmationAndMockPaymentStatements() throws Exception {
        Configuration configuration = new Configuration();
        configuration.addMapper(BillMapper.class);
        String resource = "mapper/bill/BillMapper.xml";

        try (Reader reader = Resources.getResourceAsReader(resource)) {
            new XMLMapperBuilder(reader, configuration, resource, configuration.getSqlFragments()).parse();
        }

        assertTrue(configuration.hasStatement("com.silvertown.domain.bill.mapper.BillMapper.insert"));
        assertTrue(configuration.hasStatement("com.silvertown.domain.bill.mapper.BillMapper.findOwnedByIdForUpdate"));
        assertTrue(configuration.hasStatement("com.silvertown.domain.bill.mapper.BillMapper.confirm"));
        assertTrue(configuration.hasStatement("com.silvertown.domain.bill.mapper.BillMapper.insertPayment"));
        assertTrue(configuration.hasStatement("com.silvertown.domain.bill.mapper.BillMapper.markPaid"));
        assertTrue(configuration.hasStatement(
                "com.silvertown.domain.bill.mapper.BillMapper.summarizeOwnedByDueDateRange"));
        assertTrue(configuration.hasStatement(
                "com.silvertown.domain.bill.mapper.BillMapper.findOwnedByDueDateRange"));
    }

    @Test
    void monthlySummaryItemsQueryHasAnExplicitLimit() throws Exception {
        String resource = "mapper/bill/BillMapper.xml";
        String mapperXml;
        try (var stream = Resources.getResourceAsStream(resource)) {
            mapperXml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        int queryStart = mapperXml.indexOf("<select id=\"findOwnedByDueDateRange\"");
        int queryEnd = mapperXml.indexOf("</select>", queryStart);
        assertTrue(queryStart >= 0 && queryEnd > queryStart);
        assertTrue(mapperXml.substring(queryStart, queryEnd).contains("LIMIT #{size}"));
    }
}
