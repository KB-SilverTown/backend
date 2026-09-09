package com.silvertown.domain.voice.validation;

public final class VoiceIdentifierPattern {
    public static final String CANONICAL_UUID_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    public static final String CANONICAL_UUID_REGEX = "^" + CANONICAL_UUID_PATTERN + "$";

    private VoiceIdentifierPattern() {
    }
}
