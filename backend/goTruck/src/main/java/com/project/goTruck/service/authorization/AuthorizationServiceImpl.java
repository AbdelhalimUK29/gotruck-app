package com.project.goTruck.service.authorization;

import com.project.goTruck.entity.user.User;
import com.project.goTruck.feature.FeatureRoleEnum;
import com.project.goTruck.repository.UserFeatureRepository;
import com.project.goTruck.repository.UserRepository;
import com.project.goTruck.repository.UserRentalRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@AllArgsConstructor
public class AuthorizationServiceImpl implements AuthorizationService {

    private final UserRepository userRepository;
    private final UserFeatureRepository userFeatureRepository;
    private final UserRentalRepository userRentalRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean hasFeature(Long userId, FeatureRoleEnum feature) {
        return userFeatureRepository.existsByUserIdAndFeatureName(userId, feature);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasRentalAccess(Long userId, Long rentalId) {
        return userRentalRepository.existsByUserIdAndRentalId(userId, rentalId);
    }

    @Override
    @Transactional(readOnly = true)
    public User getUserByKeycloakId(UUID keycloakId) {
        return userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new IllegalStateException("User not found in local DB: " + keycloakId));
    }
}