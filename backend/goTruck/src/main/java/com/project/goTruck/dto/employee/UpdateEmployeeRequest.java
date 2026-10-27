package com.project.goTruck.dto.employee;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.project.goTruck.config.FeatureRoleEnumDeserializer;
import com.project.goTruck.feature.FeatureRoleEnum;

import java.util.Set;

public record UpdateEmployeeRequest(

        Boolean superAdmin,

        @JsonDeserialize(using = FeatureRoleEnumDeserializer.class)
        Set<FeatureRoleEnum> features

) {
    public boolean hasChanges() {
        return superAdmin != null || (features != null && !features.isEmpty());
    }
}