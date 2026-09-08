package com.silvertown.domain.voice.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class VoiceReplayPayloadResponse {
    private final String ttsText;
    private final String ttsSsml;
    private final JsonNode displayCard;
}
