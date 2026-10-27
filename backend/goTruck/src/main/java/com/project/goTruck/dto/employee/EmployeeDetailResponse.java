package com.project.goTruck.dto.employee;

import java.time.Instant;
import java.util.Set;

public record EmployeeDetailResponse(

        Long id,

        String username,

        String email,

        String firstName,

        String lastName,

        Boolean superAdmin,

        Set<UserFeatureResponse> userFeatures

) {
}