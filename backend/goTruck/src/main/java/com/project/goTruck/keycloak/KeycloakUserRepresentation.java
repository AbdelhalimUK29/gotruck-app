package com.project.goTruck.keycloak;

import java.util.HashMap;
import java.util.Map;

public record KeycloakUserRepresentation(
        String id,
        String username,
        String email,
        String firstName,
        String lastName,
        Map<String, String> attributes
) {

    public KeycloakUserRepresentation {
        if (attributes == null) {
            attributes = new HashMap<>();
        }
    }
}