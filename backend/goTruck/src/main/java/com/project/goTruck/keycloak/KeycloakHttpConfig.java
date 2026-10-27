package com.project.goTruck.keycloak;

import com.project.goTruck.config.ProvisioningProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * HTTP configuration for all Keycloak admin calls.
 *
 * Bounded connect/read timeouts are a hard requirement of the outbox
 * worker: an unbounded call against a hung Keycloak would hold the
 * worker thread and its lease indefinitely. The timeouts are sized so
 * that a full provisioning attempt (token fetch + GET user + PUT
 * user) always completes well within leaseDuration.
 */
@Configuration
@RequiredArgsConstructor
public class KeycloakHttpConfig {

    private final ProvisioningProperties properties;

    @Bean
    public RestClient keycloakRestClient() {
        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(
                durationToMillis(properties.getHttpConnectTimeout())
        );
        factory.setReadTimeout(
                durationToMillis(properties.getHttpReadTimeout())
        );

        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    private int durationToMillis(Duration duration) {
        long millis = duration.toMillis();
        if (millis <= 0 || millis > Integer.MAX_VALUE) {
            throw new IllegalStateException(
                    "Invalid Keycloak HTTP timeout: " + duration
            );
        }
        return (int) millis;
    }
}
