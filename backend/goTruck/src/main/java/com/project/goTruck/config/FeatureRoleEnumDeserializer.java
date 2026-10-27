package com.project.goTruck.config;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.project.goTruck.exception.InvalidFeatureException;
import com.project.goTruck.feature.FeatureRoleEnum;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class FeatureRoleEnumDeserializer extends JsonDeserializer<Set<FeatureRoleEnum>> {

    @Override
    public Set<FeatureRoleEnum> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
        JsonNode node = p.getCodec().readTree(p);
        Set<FeatureRoleEnum> result = new HashSet<>();
        Set<String> invalidValues = new HashSet<>();

        if (node.isArray()) {
            for (JsonNode item : node) {
                String value = item.asText();
                FeatureRoleEnum feature = FeatureRoleEnum.fromValue(value);
                if (feature == null) {
                    invalidValues.add(value);
                } else {
                    result.add(feature);
                }
            }
        }

        if (!invalidValues.isEmpty()) {
            throw new InvalidFeatureException("Invalid feature values: " + invalidValues, invalidValues);
        }

        return result;
    }
}