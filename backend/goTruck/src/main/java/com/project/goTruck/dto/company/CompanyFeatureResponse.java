package com.project.goTruck.dto.company;

import java.time.Instant;

public record CompanyFeatureResponse(

        Long id,

        Long featureId,

        String featureName,

        Instant createdAt

) {
}