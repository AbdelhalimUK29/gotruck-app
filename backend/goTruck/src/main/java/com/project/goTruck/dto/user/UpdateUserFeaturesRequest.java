package com.project.goTruck.dto.user;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.project.goTruck.config.FeatureRoleEnumDeserializer;
import com.project.goTruck.feature.FeatureRoleEnum;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record UpdateUserFeaturesRequest(

        @NotNull(message = "Features set is required")
        @JsonDeserialize(using = FeatureRoleEnumDeserializer.class)
        Set<FeatureRoleEnum> features

) {
}