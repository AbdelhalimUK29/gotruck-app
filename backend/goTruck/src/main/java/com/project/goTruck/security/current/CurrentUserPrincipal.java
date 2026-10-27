package com.project.goTruck.security.current;

import java.util.Set;
import java.util.UUID;

public record CurrentUserPrincipal(

        UUID keycloakId,

        Long companyId,

        String email,

        String username,

        String firstName,

        String lastName,

        Set<String> authorities

) {
}