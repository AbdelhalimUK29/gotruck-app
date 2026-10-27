package com.project.goTruck.dto.user;

import java.util.UUID;

public record UserProfileResponse(
        Long id,
        UUID keycloakId,
        Long companyId,
        String username,
        String email,
        String firstName,
        String lastName,
        Boolean owner,
        Boolean superAdmin
) {
}