package com.silvertown.domain.transfer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvertown.domain.transfer.dto.AmountValidationRequest;
import com.silvertown.domain.transfer.dto.AmountValidationResponse;
import com.silvertown.domain.transfer.service.impl.TransferServiceImpl;
import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.domain.transfer.mms.MockMmsSender;
import com.silvertown.global.security.SensitiveDataHasher;
import java.time.Clock;
import org.junit.jupiter.api.Test;

class TransferAmountValidationTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TransferService service = new TransferServiceImpl(
            null, null, null, null, null, objectMapper, Clock.systemUTC(),
            new SensitiveDataHasher("test-guardian-hmac-secret"), new MockMmsSender(false));

    @Test
    void acceptsCertainPositiveAmount() throws Exception {
        AmountValidationResponse response = service.validateAmount(request(
                "{\"transcript\":\"십만원 보내 주세요\",\"recognizedAmount\":100000}"));
        assertFalse(response.isAmountReconfirmRequired());
        assertEquals(100000L, response.getConfirmedAmount());
    }

    @Test
    void rejectsMissingZeroAndNegativeAmounts() throws Exception {
        assertInvalid("{}");
        assertInvalid("{\"transcript\":\"만원\",\"recognizedAmount\":0}");
        assertInvalid("{\"transcript\":\"만원\",\"recognizedAmount\":-1}");
        assertInvalid("{\"recognizedAmount\":10000}");
    }

    @Test
    void transcriptMismatchOrMultipleAmountsAreNotAutomaticallyConfirmed() throws Exception {
        AmountValidationResponse mismatch = service.validateAmount(request(
                "{\"transcript\":\"십만원 보내 주세요\",\"recognizedAmount\":10000}"));
        AmountValidationResponse multiple = service.validateAmount(request(
                "{\"transcript\":\"만원과 십만원 중 하나\",\"recognizedAmount\":10000}"));
        assertTrue(mismatch.isAmountReconfirmRequired());
        assertNull(mismatch.getConfirmedAmount());
        assertTrue(multiple.isAmountReconfirmRequired());
        assertNull(multiple.getConfirmedAmount());
    }

    @Test
    void rejectsInvalidLegacyCandidateLists() throws Exception {
        assertInvalid("{\"amountCandidates\":[10000,10000]}");
        assertInvalid("{\"amountCandidates\":[10000,20000,30000,40000]}");
        assertInvalid("{\"recognizedAmount\":10000,\"amountCandidates\":[20000]}");
    }

    private AmountValidationRequest request(String json) throws Exception {
        return objectMapper.readValue(json, AmountValidationRequest.class);
    }

    private void assertInvalid(String json) throws Exception {
        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.validateAmount(request(json)));
        assertEquals("INVALID_TRANSFER_AMOUNT", exception.getErrorCode().getCode());
    }
}
