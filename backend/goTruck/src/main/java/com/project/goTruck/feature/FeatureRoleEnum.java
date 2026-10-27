package com.project.goTruck.feature;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum FeatureRoleEnum {

    RESERVATION,
    CLIENTS,
    TRUCKS,
    LONG_TIME_RENTAL,
    RENTAL,
    ADMIN,
    FINANCIAL_REPORTING;

    @JsonValue
    public String getValue() {
        return name();
    }

    @JsonCreator
    public static FeatureRoleEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        try {
            return FeatureRoleEnum.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null; // Jackson will handle null as invalid -> 400 with custom deserializer
        }
    }
}