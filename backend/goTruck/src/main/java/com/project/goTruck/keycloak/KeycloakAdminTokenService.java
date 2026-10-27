package com.project.goTruck.keycloak;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class KeycloakAdminTokenService {

    /** Bounded timeouts; see KeycloakHttpConfig. */
    private final RestClient restClient;

    @Value("${KEYCLOAK_SERVER_URL}")
    private String serverUrl;

    @Value("${KEYCLOAK_ADMIN_REALM}")
    private String realm;

    @Value("${KEYCLOAK_ADMIN_CLIENT_ID}")
    private String clientId;

    @Value("${KEYCLOAK_ADMIN_CLIENT_SECRET}")
    private String clientSecret;

    public String getAccessToken() {

        var form = new LinkedMultiValueMap<String, String>();

        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        var response = restClient
                .post()
                .uri(serverUrl + "/realms/" + realm + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(KeycloakTokenResponse.class);

        if (response == null || response.accessToken() == null) {
            throw new IllegalStateException(
                    "Failed to obtain Keycloak admin access token"
            );
        }

        return response.accessToken();
    }

    private record KeycloakTokenResponse(
            String access_token
    ) {
        public String accessToken() {
            return access_token;
        }
    }
}