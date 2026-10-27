package com.project.goTruck.controller.manager;

import com.project.goTruck.dto.user.UpdateUserFeaturesRequest;
import com.project.goTruck.dto.user.UserProfileResponse;
import com.project.goTruck.dto.user.UserProfileWithFeaturesResponse;
import com.project.goTruck.mapper.user.UserMapper;
import com.project.goTruck.security.argumentresolver.SessionCompanyId;
import com.project.goTruck.security.current.CurrentUserPrincipal;
import com.project.goTruck.security.current.CurrentUserService;
import com.project.goTruck.service.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/manager/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;
    private final CurrentUserService currentUserService;

    /**
     * Updates feature assignments for a user within the current company.
     *
     * The company is derived from the authenticated user's session via @SessionCompanyId.
     * Only company owners or super admins can update user features.
     *
     * @param userId   Target user ID
     * @param request  Set of FeatureRoleEnum (RESERVATION, CLIENTS, TRUCKS, LONG_TIME_RENTAL, FINANCIAL_REPORTING)
     * @param companyId Company ID from session (injected by SessionCompanyIdArgumentResolver)
     * @return 204 No Content on success
     */
    @PutMapping("/{userId}/features")
    public ResponseEntity<Void> updateUserFeatures(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserFeaturesRequest request,
            @SessionCompanyId Long companyId
    ) {
        log.info("Updating features for user: {} in company: {}", userId, companyId);
        userService.updateUserFeatures(companyId, userId, request.features());
        return ResponseEntity.noContent().build();
    }

    /**
     * Retrieves the profile of the currently authenticated user.
     *
     * The Keycloak ID in the path must match the authenticated user's Keycloak ID.
     * This endpoint allows users to fetch their own profile information.
     *
     * @param keycloakId Keycloak UUID from path
     * @return UserProfileResponse with user profile details (id, keycloakId, companyId, username, email, firstName, lastName, owner, superAdmin)
     */
    @GetMapping("/profile/{keycloakId}")
    public ResponseEntity<UserProfileResponse> getUserProfile(
            @PathVariable UUID keycloakId
    ) {
        log.info("Fetching user profile for keycloakId: {}", keycloakId);
        CurrentUserPrincipal currentUser = currentUserService.currentUser();
        UserProfileResponse profile = userService.getUserProfileByKeycloakId(keycloakId, currentUser.keycloakId());
        return ResponseEntity.ok(profile);
    }

    /**
     * Retrieves the profile of the currently authenticated user with their assigned features.
     *
     * The user is identified from the current authentication context (Keycloak ID).
     * The company is derived from the authenticated user's session via @SessionCompanyId.
     * Features are fetched in a single query using JOIN FETCH to avoid N+1 problem.
     *
     * @param companyId Company ID from session (injected by SessionCompanyIdArgumentResolver)
     * @return UserProfileWithFeaturesResponse with user profile details and assigned features
     */
    @GetMapping("/me")
    public ResponseEntity<UserProfileWithFeaturesResponse> getCurrentUserProfileWithFeatures(
            @SessionCompanyId Long companyId
    ) {
        CurrentUserPrincipal currentUser = currentUserService.currentUser();
        log.info("Fetching current user profile with features for keycloakId: {} in company: {}", currentUser.keycloakId(), companyId);
        UserProfileWithFeaturesResponse profile = userService.getCurrentUserProfileWithFeatures(companyId, currentUser.keycloakId());
        return ResponseEntity.ok(profile);
    }
}