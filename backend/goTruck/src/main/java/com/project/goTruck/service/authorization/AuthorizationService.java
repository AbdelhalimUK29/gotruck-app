package com.project.goTruck.service.authorization;

import com.project.goTruck.entity.user.User;
import com.project.goTruck.feature.FeatureRoleEnum;

import java.util.UUID;

public interface AuthorizationService {

    boolean hasFeature(Long userId, FeatureRoleEnum feature);

    boolean hasRentalAccess(Long userId, Long rentalId);

    User getUserByKeycloakId(UUID keycloakId);
}