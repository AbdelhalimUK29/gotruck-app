package com.project.goTruck.dto.feature;

import com.project.goTruck.feature.FeatureRoleEnum;

public record FeatureResponse(
        Long id,
        FeatureRoleEnum name
) {
}