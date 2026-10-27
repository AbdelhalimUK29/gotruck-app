package com.project.goTruck.mapper.user;

import com.project.goTruck.dto.user.UserProfileResponse;
import com.project.goTruck.dto.user.UserProfileWithFeaturesResponse;
import com.project.goTruck.entity.user.User;
import com.project.goTruck.feature.FeatureRoleEnum;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserMapper INSTANCE = Mappers.getMapper(UserMapper.class);

    /**
     * Maps User entity to UserProfileResponse.
     * Direct field mapping: id, keycloakId, companyId, username, email, firstName, lastName, owner, superAdmin
     */
    @Mapping(target = "companyId", source = "company.id")
    UserProfileResponse toProfileResponse(User user);

    /**
     * Maps User entity to UserProfileWithFeaturesResponse.
     * Includes user features by extracting FeatureRoleEnum from userFeatures collection.
     */
    @Mapping(target = "companyId", source = "company.id")
    @Mapping(target = "features", expression = "java(mapUserFeatures(user))")
    UserProfileWithFeaturesResponse toProfileWithFeaturesResponse(User user);

    default Set<FeatureRoleEnum> mapUserFeatures(User user) {
        if (user.getUserFeatures() == null) {
            return Set.of();
        }
        return user.getUserFeatures().stream()
                .map(uf -> uf.getFeature().getName())
                .collect(Collectors.toSet());
    }
}