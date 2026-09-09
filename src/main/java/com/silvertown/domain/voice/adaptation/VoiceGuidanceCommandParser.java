package com.silvertown.domain.voice.adaptation;

import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Parses only the documented Korean guidance commands; all other text remains a normal voice turn. */
@Component
public class VoiceGuidanceCommandParser {
    private static final Map<String, VoiceGuidanceCommand> COMMANDS = Map.ofEntries(
            Map.entry("천천히말해줘", VoiceGuidanceCommand.SLOWER),
            Map.entry("느리게말해줘", VoiceGuidanceCommand.SLOWER),
            Map.entry("천천히", VoiceGuidanceCommand.SLOWER),
            Map.entry("좀느리게", VoiceGuidanceCommand.SLOWER),
            Map.entry("빨리말해줘", VoiceGuidanceCommand.FASTER),
            Map.entry("빠르게말해줘", VoiceGuidanceCommand.FASTER),
            Map.entry("좀빨리", VoiceGuidanceCommand.FASTER),
            Map.entry("원래대로", VoiceGuidanceCommand.DEFAULT_SPEED),
            Map.entry("기본속도로", VoiceGuidanceCommand.DEFAULT_SPEED),
            Map.entry("보통속도로", VoiceGuidanceCommand.DEFAULT_SPEED),
            Map.entry("크게말해줘", VoiceGuidanceCommand.LOUDER),
            Map.entry("더크게", VoiceGuidanceCommand.LOUDER),
            Map.entry("소리가작아", VoiceGuidanceCommand.LOUDER),
            Map.entry("잘안들려", VoiceGuidanceCommand.LOUDER),
            Map.entry("목소리가안들려", VoiceGuidanceCommand.LOUDER),
            Map.entry("작게말해줘", VoiceGuidanceCommand.QUIETER),
            Map.entry("소리가너무커", VoiceGuidanceCommand.QUIETER),
            Map.entry("목소리가커", VoiceGuidanceCommand.QUIETER),
            Map.entry("원래볼륨으로", VoiceGuidanceCommand.DEFAULT_VOLUME),
            Map.entry("기본음량으로", VoiceGuidanceCommand.DEFAULT_VOLUME));

    public Optional<VoiceGuidanceCommand> parse(String transcript) {
        if (transcript == null) {
            return Optional.empty();
        }
        String normalized = transcript.replaceAll("[\\s.,!?]", "").trim();
        return Optional.ofNullable(COMMANDS.get(normalized));
    }
}
