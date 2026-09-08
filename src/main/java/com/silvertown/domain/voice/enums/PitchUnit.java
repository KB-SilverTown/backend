package com.silvertown.domain.voice.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PitchUnit {
    HZ("Hz"),
    SEMITONE("st"),
    RELATIVE("relative");

    private final String databaseValue;

    PitchUnit(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    @JsonValue
    public String getDatabaseValue() {
        return databaseValue;
    }

    @JsonCreator
    public static PitchUnit fromDatabaseValue(String databaseValue) {
        for (PitchUnit pitchUnit : values()) {
            if (pitchUnit.databaseValue.equals(databaseValue)) {
                return pitchUnit;
            }
        }
        throw new IllegalArgumentException("지원하지 않는 피치 단위입니다: " + databaseValue);
    }
}
