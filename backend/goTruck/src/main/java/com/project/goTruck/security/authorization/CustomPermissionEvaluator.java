package com.project.goTruck.security.authorization;

import com.project.goTruck.entity.user.User;
import com.project.goTruck.feature.FeatureRoleEnum;
import com.project.goTruck.security.current.CurrentUserAuthenticationToken;
import com.project.goTruck.security.current.CurrentUserPrincipal;
import com.project.goTruck.service.authorization.AuthorizationService;
import lombok.AllArgsConstructor;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.UUID;

@Component
@AllArgsConstructor
public class CustomPermissionEvaluator implements PermissionEvaluator {

    private final AuthorizationService authorizationService;

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        if (!(authentication instanceof CurrentUserAuthenticationToken currentUserAuth)) {
            return false;
        }

        Object principalObj = currentUserAuth.getPrincipal();
        if (!(principalObj instanceof CurrentUserPrincipal principal)) {
            return false;
        }

        UUID keycloakId = principal.keycloakId();

        // Resolve user once - single query
        User user = authorizationService.getUserByKeycloakId(keycloakId);
        Long userId = user.getId();

        // Fast path: owner or superAdmin get all permissions (no extra query)
        if (user.isOwner() || user.isSuperAdmin()) {
            return true;
        }

        // Resolve feature permission
        FeatureRoleEnum feature = resolveFeature(permission);
        if (feature == null) {
            return false;
        }

        // If rentalId is provided (non-null), verify rental access regardless of feature
        if (targetDomainObject instanceof Long rentalId && rentalId != null) {
            if (!authorizationService.hasRentalAccess(userId, rentalId)) {
                return false;
            }
        }

        // Check feature access
        return authorizationService.hasFeature(userId, feature);
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType, Object permission) {
        if (!(authentication instanceof CurrentUserAuthenticationToken currentUserAuth)) {
            return false;
        }

        Object principalObj = currentUserAuth.getPrincipal();
        if (!(principalObj instanceof CurrentUserPrincipal principal)) {
            return false;
        }

        UUID keycloakId = principal.keycloakId();

        // Resolve user once - single query
        User user = authorizationService.getUserByKeycloakId(keycloakId);
        Long userId = user.getId();

        // Fast path: owner or superAdmin get all permissions (no extra query)
        if (user.isOwner() || user.isSuperAdmin()) {
            return true;
        }

        // Resolve feature permission
        FeatureRoleEnum feature = resolveFeature(permission);
        if (feature == null) {
            return false;
        }

        // If rentalId is provided by ID and type, verify rental access
        if (targetId instanceof Long rentalId && rentalId != null) {
            if (!authorizationService.hasRentalAccess(userId, rentalId)) {
                return false;
            }
        }

        return authorizationService.hasFeature(userId, feature);
    }

    private FeatureRoleEnum resolveFeature(Object permission) {
        if (permission instanceof FeatureRoleEnum feature) {
            return feature;
        }
        if (permission instanceof String featureName) {
            try {
                return FeatureRoleEnum.fromValue(featureName);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}