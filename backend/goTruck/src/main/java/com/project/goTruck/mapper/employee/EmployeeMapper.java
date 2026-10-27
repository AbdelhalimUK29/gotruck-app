package com.project.goTruck.mapper.employee;

import com.project.goTruck.dto.employee.CreateEmployeeRequest;
import com.project.goTruck.dto.employee.EmployeeDetailResponse;
import com.project.goTruck.dto.employee.EmployeeResponse;
import com.project.goTruck.dto.employee.UserFeatureResponse;
import com.project.goTruck.entity.UserFeature;
import com.project.goTruck.entity.user.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", unmappedTargetPolicy = org.mapstruct.ReportingPolicy.ERROR)
public interface EmployeeMapper {

    EmployeeMapper INSTANCE = Mappers.getMapper(EmployeeMapper.class);

    /**
     * Maps CreateEmployeeRequest to User entity.
     * Fields keycloakId, company, owner, superAdmin, id, userFeatures, userRentals, fullName are set manually in service.
     * fullName is computed from firstName + lastName via @PrePersist.
     */
    @Mapping(target = "keycloakId", ignore = true)
    @Mapping(target = "company", ignore = true)
    @Mapping(target = "owner", constant = "false")
    @Mapping(target = "superAdmin", constant = "false")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userFeatures", ignore = true)
    @Mapping(target = "userRentals", ignore = true)
    @Mapping(target = "fullName", ignore = true)
    User toEntity(CreateEmployeeRequest request);

    /**
     * Maps User entity to EmployeeResponse.
     * Direct field mapping: id, username, email, firstName, lastName
     */
    EmployeeResponse toResponse(User user);

    /**
     * Maps a list of User entities to EmployeeResponse list.
     */
    default List<EmployeeResponse> toResponseList(List<User> users) {
        return users.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Maps a Page of User entities to Page of EmployeeResponse.
     * Preserves pagination metadata (totalElements, totalPages, etc.)
     */
    default Page<EmployeeResponse> toResponsePage(Page<User> userPage) {
        List<EmployeeResponse> content = userPage.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return new PageImpl<>(content, userPage.getPageable(), userPage.getTotalElements());
    }

    /**
     * Maps User entity to EmployeeDetailResponse with user features.
     * Assumes userFeatures and feature are already loaded (via fetch join).
     */
    @Mapping(target = "userFeatures", expression = "java(mapUserFeatures(user))")
    EmployeeDetailResponse toDetailResponse(User user);

    default Set<UserFeatureResponse> mapUserFeatures(User user) {
        return user.getUserFeatures() == null ? Set.of() :
                user.getUserFeatures().stream()
                        .map(this::toUserFeatureResponse)
                        .collect(Collectors.toSet());
    }

    default UserFeatureResponse toUserFeatureResponse(UserFeature uf) {
        return new UserFeatureResponse(
                uf.getId(),
                uf.getFeature().getId(),
                uf.getFeature().getName().name(),
                uf.getCreatedAt()
        );
    }
}