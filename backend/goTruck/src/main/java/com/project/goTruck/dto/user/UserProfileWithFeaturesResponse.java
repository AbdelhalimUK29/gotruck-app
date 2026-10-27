package com.project.goTruck.dto.user;

import com.project.goTruck.feature.FeatureRoleEnum;
import java.util.Set;
import java.util.UUID;

public record UserProfileWithFeaturesResponse(
        Long id,
        UUID keycloakId,
        Long companyId,
        String username,
        String email,
        String firstName,
        String lastName,
        Boolean owner,
        Boolean superAdmin,
        Set<FeatureRoleEnum> features
) {
}