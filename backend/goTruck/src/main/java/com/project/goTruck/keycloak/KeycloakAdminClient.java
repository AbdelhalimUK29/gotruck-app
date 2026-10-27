package com.project.goTruck.keycloak;

import com.project.goTruck.exception.KeycloakUserCreationException;
import com.project.goTruck.exception.KeycloakUserDeletionException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class KeycloakAdminClient {

    private final KeycloakAdminTokenService tokenService;

    /** Bounded timeouts; see KeycloakHttpConfig. */
    private final RestClient restClient;

    @Value("${KEYCLOAK_SERVER_URL}")
    private String serverUrl;

    @Value("${KEYCLOAK_ADMIN_REALM}")
    private String realm;

    public void updateUserCompanyId(
            UUID keycloakUserId,
            String companyId
    ) {

        String token = tokenService.getAccessToken();

        var user = restClient
                .get()
                .uri(
                        serverUrl
                                + "/admin/realms/"
                                + realm
                                + "/users/"
                                + keycloakUserId
                )
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + token
                )
                .retrieve()
                .body(KeycloakUserRepresentation.class);

        if (user == null) {
            throw new IllegalStateException(
                    "Keycloak user not found: " + keycloakUserId
            );
        }

        // Update only the company_id attribute, preserving all other data
        user.attributes().put("company_id", companyId);

        restClient
                .put()
                .uri(
                        serverUrl
                                + "/admin/realms/"
                                + realm
                                + "/users/"
                                + keycloakUserId
                )
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + token
                )
                .contentType(MediaType.APPLICATION_JSON)
                .body(user)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Creates a new user in Keycloak with company_id attribute.
     *
     * @return the Keycloak user ID (UUID) of the created user
     * @throws KeycloakUserCreationException if user already exists (409) or other Keycloak error
     */
    public UUID createUser(
            String username,
            String email,
            String firstName,
            String lastName,
            String temporaryPassword,
            String companyId
    ) {
        String token = tokenService.getAccessToken();

        var userRepresentation = Map.<String, Object>of(
                "username", username,
                "email", email,
                "firstName", firstName,
                "lastName", lastName,
                "enabled", true,
                "emailVerified", false,
                "attributes", Map.of("company_id", companyId),
                "credentials", List.of(Map.of(
                        "type", "password",
                        "value", temporaryPassword,
                        "temporary", true
                ))
        );

        try {
            var response = restClient
                    .post()
                    .uri(serverUrl + "/admin/realms/" + realm + "/users")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(userRepresentation)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        if (res.getStatusCode().value() == 409) {
                            throw new KeycloakUserCreationException(
                                    "User already exists in Keycloak: " + username);
                        }
                        throw new KeycloakUserCreationException(
                                "Keycloak client error: " + res.getStatusCode());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new KeycloakUserCreationException(
                                "Keycloak server error: " + res.getStatusCode());
                    })
                    .toBodilessEntity();

            // Extract user ID from Location header
            URI location = response.getHeaders().getLocation();
            if (location == null) {
                throw new KeycloakUserCreationException("Keycloak did not return Location header for created user");
            }

            String path = location.getPath();
            String userId = path.substring(path.lastIndexOf('/') + 1);
            return UUID.fromString(userId);

        } catch (KeycloakUserCreationException e) {
            throw e;
        } catch (Exception e) {
            throw new KeycloakUserCreationException("Failed to create user in Keycloak", e);
        }
    }

    /**
     * Deletes a user from Keycloak by ID.
     * Used for compensation when local DB transaction fails.
     * @throws KeycloakUserDeletionException if deletion fails (except 404 which is ignored)
     */
    public void deleteUser(UUID keycloakUserId) {
        String token = tokenService.getAccessToken();

        try {
            restClient
                    .delete()
                    .uri(serverUrl + "/admin/realms/" + realm + "/users/" + keycloakUserId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        if (res.getStatusCode().value() == 404) {
                            // Already deleted - fine for compensation
                            return;
                        }
                        throw new KeycloakUserDeletionException(
                                "Failed to delete Keycloak user: " + res.getStatusCode());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new KeycloakUserDeletionException(
                                "Keycloak server error during delete: " + res.getStatusCode());
                    })
                    .toBodilessEntity();
        } catch (KeycloakUserDeletionException e) {
            throw e;
        } catch (Exception e) {
            throw new KeycloakUserDeletionException("Failed to delete Keycloak user", e);
        }
    }
}