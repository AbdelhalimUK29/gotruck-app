package com.project.goTruck.service.user;

import com.project.goTruck.dto.user.UserProfileResponse;
import com.project.goTruck.dto.user.UserProfileWithFeaturesResponse;
import com.project.goTruck.entity.Company;
import com.project.goTruck.entity.CompanyFeature;
import com.project.goTruck.entity.Feature;
import com.project.goTruck.entity.UserFeature;
import com.project.goTruck.entity.user.User;
import com.project.goTruck.exception.CompanyNotFoundException;
import com.project.goTruck.exception.FeatureNotEnabledForCompanyException;
import com.project.goTruck.exception.InvalidFeatureException;
import com.project.goTruck.exception.UserNotFoundException;
import com.project.goTruck.feature.FeatureRoleEnum;
import com.project.goTruck.mapper.user.UserMapper;
import com.project.goTruck.repository.CompanyFeatureRepository;
import com.project.goTruck.repository.CompanyRepository;
import com.project.goTruck.repository.FeatureRepository;
import com.project.goTruck.repository.UserFeatureRepository;
import com.project.goTruck.repository.UserRepository;
import com.project.goTruck.security.current.CurrentUserPrincipal;
import com.project.goTruck.security.current.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserFeatureRepository userFeatureRepository;
    private final CompanyFeatureRepository companyFeatureRepository;
    private final FeatureRepository featureRepository;
    private final CompanyRepository companyRepository;
    private final CurrentUserService currentUserService;
    private final UserMapper userMapper;

    /**
     * Updates a user's feature assignments within a company.
     *
     * Flow:
     * 1. Validate current user is owner OR superAdmin of the company
     * 2. Validate target user exists and belongs to the company
     * 3. Load company's enabled features
     * 4. Validate all requested features are enabled for the company
     * 5. Calculate diff (toAdd, toRemove) from current user features
     * 6. Apply changes atomically within transaction
     *
     * @param companyId       Company ID from @SessionCompanyId
     * @param userId          Target user ID
     * @param requestedFeatures Set of FeatureRoleEnum from request (validated by deserializer)
     */
    @Transactional
    public void updateUserFeatures(Long companyId, Long userId, Set<FeatureRoleEnum> requestedFeatures) {
        log.info("Updating features for user: {} in company: {}", userId, companyId);

        // 1. Validate requester authorization (owner OR superAdmin)
        validateRequesterAuthorization(companyId);

        // 2. Validate target user exists in this company
        User targetUser = userRepository.findByIdAndCompanyId(userId, companyId)
                .orElseThrow(() -> new UserNotFoundException("User not found in this company: " + userId));

        // 3. Load company's enabled features
        Set<FeatureRoleEnum> companyFeatures = getCompanyFeatures(companyId);

        // 4. Validate all requested features are enabled for the company
        validateFeaturesEnabledForCompany(requestedFeatures, companyFeatures);

        // 5. Load current user features
        Set<FeatureRoleEnum> currentFeatures = getUserCurrentFeatures(userId);

        // 6. Calculate diff
        Set<FeatureRoleEnum> toRemove = new HashSet<>(currentFeatures);
        toRemove.removeAll(requestedFeatures);

        Set<FeatureRoleEnum> toAdd = new HashSet<>(requestedFeatures);
        toAdd.removeAll(currentFeatures);

        log.debug("Feature diff for user {}: toAdd={}, toRemove={}", userId, toAdd, toRemove);

        // 7. Apply removals
        if (!toRemove.isEmpty()) {
            userFeatureRepository.deleteByUserIdAndFeatureNames(userId, toRemove);
            log.info("Removed features for user {}: {}", userId, toRemove);
        }

        // 8. Apply additions
        if (!toAdd.isEmpty()) {
            addFeaturesToUser(targetUser, toAdd);
            log.info("Added features for user {}: {}", userId, toAdd);
        }
    }

    /**
     * Validates that the current authenticated user is owner OR superAdmin of the company.
     */
    private void validateRequesterAuthorization(Long companyId) {
        CurrentUserPrincipal currentUser = currentUserService.currentUser();
        UUID managerKeycloakId = currentUser.keycloakId();

        User manager = userRepository.findByKeycloakId(managerKeycloakId)
                .orElseThrow(() -> new IllegalStateException("Manager not found in local DB"));

        if (!companyId.equals(currentUser.companyId())) {
            throw new SecurityException("Unauthorized for this company");
        }

        if (!manager.isOwner() && !manager.isSuperAdmin()) {
            throw new SecurityException("Only company owner or super admin can update user features");
        }
    }

    
    /**
     * Loads all features enabled for the company.
     */
    private Set<FeatureRoleEnum> getCompanyFeatures(Long companyId) {
        List<CompanyFeature> companyFeatures = companyFeatureRepository.findByCompanyId(companyId);
        return companyFeatures.stream()
                .map(cf -> cf.getFeature().getName())
                .collect(Collectors.toSet());
    }

    /**
     * Validates that all requested features are enabled for the company.
     */
    private void validateFeaturesEnabledForCompany(Set<FeatureRoleEnum> requestedFeatures, Set<FeatureRoleEnum> companyFeatures) {
        Set<FeatureRoleEnum> notEnabled = new HashSet<>(requestedFeatures);
        notEnabled.removeAll(companyFeatures);

        if (!notEnabled.isEmpty()) {
            Set<String> notEnabledNames = notEnabled.stream()
                    .map(Enum::name)
                    .collect(Collectors.toSet());
            throw new FeatureNotEnabledForCompanyException(
                    "The following features are not enabled for this company: " + notEnabledNames,
                    notEnabledNames
            );
        }
    }

    /**
     * Loads current features assigned to the user.
     */
    private Set<FeatureRoleEnum> getUserCurrentFeatures(Long userId) {
        List<UserFeature> userFeatures = userFeatureRepository.findByUserId(userId);
        return userFeatures.stream()
                .map(uf -> uf.getFeature().getName())
                .collect(Collectors.toSet());
    }

    /**
     * Adds features to user by creating UserFeature entities.
     */
    private void addFeaturesToUser(User user, Set<FeatureRoleEnum> featuresToAdd) {
        for (FeatureRoleEnum featureName : featuresToAdd) {
            Feature feature = featureRepository.findByName(featureName)
                    .orElseThrow(() -> new IllegalStateException("Feature not found in system: " + featureName));

            UserFeature userFeature = UserFeature.builder()
                    .user(user)
                    .feature(feature)
                    .build();

            userFeatureRepository.save(userFeature);
        }
    }

    /**
     * Retrieves the profile of the current authenticated user by their Keycloak ID.
     *
     * @param keycloakId Keycloak ID from path parameter
     * @param currentUserKeycloakId Current authenticated user's Keycloak ID from session
     * @return UserProfileResponse if found and authorized
     * @throws SecurityException if keycloakId doesn't match current authenticated user
     * @throws UserNotFoundException if user not found in local DB
     */
    public UserProfileResponse getUserProfileByKeycloakId(UUID keycloakId, UUID currentUserKeycloakId) {
        // 1. Validate path keycloakId matches authenticated user's keycloakId
        if (!keycloakId.equals(currentUserKeycloakId)) {
            throw new SecurityException("Access denied: cannot access another user's profile");
        }

        // 2. Fetch user by keycloakId
        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + keycloakId));

        // 3. Map to response DTO
        return userMapper.toProfileResponse(user);
    }

    /**
     * Retrieves the profile of the current authenticated user with their assigned features.
     *
     * Fetches user by Keycloak ID and Company ID with features joined in a single query
     * to avoid N+1 problem.
     *
     * @param companyId Company ID from @SessionCompanyId
     * @param keycloakId Current authenticated user's Keycloak ID from session
     * @return UserProfileWithFeaturesResponse with user profile and features
     * @throws UserNotFoundException if user not found in local DB or not in company
     */
    @Transactional(readOnly = true)
    public UserProfileWithFeaturesResponse getCurrentUserProfileWithFeatures(Long companyId, UUID keycloakId) {
        log.info("Fetching current user profile with features for keycloakId: {} in company: {}", keycloakId, companyId);

        // 1. Fetch user by keycloakId and companyId with features joined
        User user = userRepository.findByKeycloakIdAndCompanyIdWithUserFeatures(keycloakId, companyId)
                .orElseThrow(() -> new UserNotFoundException("User not found in this company: " + keycloakId));

        // 2. Map to response DTO with features
        return userMapper.toProfileWithFeaturesResponse(user);
    }
}