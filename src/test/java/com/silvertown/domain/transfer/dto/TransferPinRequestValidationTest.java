package com.silvertown.domain.transfer.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import javax.validation.Validation;
import javax.validation.Validator;
import org.junit.jupiter.api.Test;

class TransferPinRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rejectsMissingPin() throws Exception {
        TransferPinRequest request = objectMapper.readValue("{}", TransferPinRequest.class);
        assertEquals(1, validator.validate(request).size());
    }

    @Test
    void rejectsBlankPin() throws Exception {
        TransferPinRequest request = objectMapper.readValue("{\"pin\":\"\"}", TransferPinRequest.class);
        assertEquals(2, validator.validate(request).size());
    }
}
