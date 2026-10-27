package com.project.goTruck.dto.company;

/**
 * Company creation response.
 *
 * provisioningStatus mirrors the company provisioning outbox state
 * (see ProvisioningStatus): the request returns PENDING because the
 * Keycloak company_id update is applied asynchronously by the outbox
 * worker. Clients poll the company status endpoint to observe
 * transitions to a terminal state.
 */
public record CompanyResponse(

        Long id,

        String name,

        String website,

        String email,

        String location,

        String city,

        String country,

        String logo,

        String provisioningStatus

) {
}
