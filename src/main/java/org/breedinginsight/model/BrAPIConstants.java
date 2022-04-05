package org.breedinginsight.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum BrAPIConstants {
    SYSTEM_DEFAULT("System Default");

    private String value;

    BrAPIConstants(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}