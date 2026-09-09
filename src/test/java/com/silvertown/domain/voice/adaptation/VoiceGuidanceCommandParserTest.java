package com.silvertown.domain.voice.adaptation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VoiceGuidanceCommandParserTest {
    private final VoiceGuidanceCommandParser parser = new VoiceGuidanceCommandParser();

    @Test
    void parsesOnlyTheDocumentedSpeedAndVolumeExpressions() {
        assertEquals(VoiceGuidanceCommand.SLOWER, parser.parse("천천히 말해줘.").orElseThrow());
        assertEquals(VoiceGuidanceCommand.FASTER, parser.parse("좀 빨리").orElseThrow());
        assertEquals(VoiceGuidanceCommand.DEFAULT_SPEED, parser.parse("기본 속도로").orElseThrow());
        assertEquals(VoiceGuidanceCommand.LOUDER, parser.parse("잘 안 들려").orElseThrow());
        assertEquals(VoiceGuidanceCommand.QUIETER, parser.parse("소리가 너무 커").orElseThrow());
        assertEquals(VoiceGuidanceCommand.DEFAULT_VOLUME, parser.parse("기본 음량으로").orElseThrow());
        assertTrue(parser.parse("천천히 돈 보내줘").isEmpty());
    }
}
