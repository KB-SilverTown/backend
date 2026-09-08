package com.silvertown.domain.voice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvertown.domain.voice.mapper.VoiceInteractionCardMapper;
import com.silvertown.domain.voice.vo.VoiceInteractionCardVo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class VoiceInteractionCardIssuerTest {
    @Test
    void focusesTheFirstOrderedCandidateButStillRequiresExplicitAcceptance() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        VoiceInteractionCardMapper mapper = org.mockito.Mockito.mock(VoiceInteractionCardMapper.class);
        when(mapper.insert(any(VoiceInteractionCardVo.class))).thenReturn(1);
        VoiceInteractionCardIssuer issuer = new VoiceInteractionCardIssuer(mapper, objectMapper);
        JsonNode displayCard = objectMapper.readTree("""
                {"type":"RECIPIENT_CANDIDATES","items":[
                  {"recipientId":"50000000-0000-0000-0000-000000000001","displayName":"김철수"}]}
                """);

        JsonNode issued = issuer.issueIfInteractive(
                "10000000-0000-0000-0000-000000000001",
                "20000000-0000-0000-0000-000000000001",
                displayCard);

        ArgumentCaptor<VoiceInteractionCardVo> captor = ArgumentCaptor.forClass(VoiceInteractionCardVo.class);
        verify(mapper).insert(captor.capture());
        assertEquals("50000000-0000-0000-0000-000000000001", captor.getValue().getFocusedItemId());
        assertEquals("50000000-0000-0000-0000-000000000001", issued.path("focusedItemId").asText());
        assertTrue(issued.path("items").get(0).path("isFocused").asBoolean());
    }

    @Test
    void preservesAnExplicitlyClearedFocusAfterSelectionIsRejected() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        VoiceInteractionCardMapper mapper = org.mockito.Mockito.mock(VoiceInteractionCardMapper.class);
        when(mapper.insert(any(VoiceInteractionCardVo.class))).thenReturn(1);
        VoiceInteractionCardIssuer issuer = new VoiceInteractionCardIssuer(mapper, objectMapper);
        JsonNode displayCard = objectMapper.readTree("""
                {"type":"RECIPIENT_CANDIDATES","focusedItemId":null,"items":[
                  {"recipientId":"50000000-0000-0000-0000-000000000001","displayName":"김철수"}]}
                """);

        JsonNode issued = issuer.issueIfInteractive(
                "10000000-0000-0000-0000-000000000001",
                "20000000-0000-0000-0000-000000000001",
                displayCard);

        ArgumentCaptor<VoiceInteractionCardVo> captor = ArgumentCaptor.forClass(VoiceInteractionCardVo.class);
        verify(mapper).insert(captor.capture());
        assertNull(captor.getValue().getFocusedItemId());
        assertTrue(issued.path("focusedItemId").isNull());
    }
}
