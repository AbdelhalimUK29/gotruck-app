package com.project.goTruck.dto.employee;

import java.time.Instant;

public record UserFeatureResponse(

        Long id,

        Long featureId,

        String featureName,

        Instant createdAt

) {
}