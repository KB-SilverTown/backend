package com.silvertown.domain.voice.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import javax.validation.Validation;
import javax.validation.Validator;
import org.junit.jupiter.api.Test;

class VoiceIdentifierDtoValidationTest {
    private static final String CANONICAL_TURN_ID = "10000000-0000-0000-0000-000000000001";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsCanonicalStringTurnIds() throws Exception {
        VoiceTurnRequest turnRequest = objectMapper.readValue(
                "{\"turnId\":\"" + CANONICAL_TURN_ID
                        + "\",\"transcript\":\"잔액을 알려줘\",\"sttConfidence\":0.95,\"inputType\":\"VOICE\"}",
                VoiceTurnRequest.class);
        VoiceSessionEventRequest eventRequest = objectMapper.readValue(
                "{\"eventType\":\"REPLAY\",\"turnId\":\"" + CANONICAL_TURN_ID + "\"}",
                VoiceSessionEventRequest.class);

        assertEquals(CANONICAL_TURN_ID, turnRequest.getTurnId());
        assertEquals(CANONICAL_TURN_ID, eventRequest.getTurnId());
        assertTrue(validator.validate(turnRequest).isEmpty());
        assertTrue(validator.validate(eventRequest).isEmpty());
    }

    @Test
    void rejectsNonCanonicalStringTurnIds() throws Exception {
        VoiceTurnRequest turnRequest = objectMapper.readValue(
                "{\"turnId\":\"10000000-0000-0000-0000-00000000000A\","
                        + "\"transcript\":\"잔액을 알려줘\",\"sttConfidence\":0.95,\"inputType\":\"VOICE\"}",
                VoiceTurnRequest.class);
        VoiceSessionEventRequest eventRequest = objectMapper.readValue(
                "{\"eventType\":\"REPLAY\",\"turnId\":\"not-a-uuid\"}",
                VoiceSessionEventRequest.class);

        assertTrue(hasTurnIdViolation(validator.validate(turnRequest)));
        assertTrue(hasTurnIdViolation(validator.validate(eventRequest)));
    }

    private boolean hasTurnIdViolation(
            Set<? extends javax.validation.ConstraintViolation<?>> violations
    ) {
        return violations.stream().anyMatch(violation -> "turnId".equals(violation.getPropertyPath().toString()));
    }
}
