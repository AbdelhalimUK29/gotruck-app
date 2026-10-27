package com.project.goTruck.security.current;

import com.project.goTruck.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CurrentUserPrincipalMapper {

    private final UserRepository userRepository;

    public CurrentUserPrincipal fromJwt(
            Jwt jwt,
            Set<String> authorities
    ) {

        UUID keycloakId = UUID.fromString(jwt.getSubject());

        // Safely extract and convert the company_id claim
        String companyIdStr = jwt.getClaimAsString("company_id");
        Long companyId = null;
        if (companyIdStr != null && !companyIdStr.isEmpty()) {
            try {
                companyId = Long.valueOf(companyIdStr);
            } catch (NumberFormatException e) {
                log.warn("Failed to convert company_id claim '{}' to Long, defaulting to null", companyIdStr);
            }
        }

        return new CurrentUserPrincipal(

                keycloakId,

                companyId,

                jwt.getClaimAsString("email"),

                jwt.getClaimAsString("preferred_username"),

                jwt.getClaimAsString("given_name"),

                jwt.getClaimAsString("family_name"),

                authorities

        );

    }

}
