package com.project.goTruck.service.company;

import com.project.goTruck.dto.company.CreateCompanyRequest;
import com.project.goTruck.entity.Company;
import com.project.goTruck.entity.CompanyFeature;
import com.project.goTruck.entity.CompanyProvisioningOutbox;
import com.project.goTruck.entity.Feature;
import com.project.goTruck.entity.UserFeature;
import com.project.goTruck.entity.user.User;
import com.project.goTruck.feature.FeatureRoleEnum;
import com.project.goTruck.repository.CompanyFeatureRepository;
import com.project.goTruck.repository.CompanyProvisioningOutboxRepository;
import com.project.goTruck.repository.CompanyRepository;
import com.project.goTruck.repository.FeatureRepository;
import com.project.goTruck.repository.UserFeatureRepository;
import com.project.goTruck.repository.UserRepository;
import com.project.goTruck.security.current.CurrentUserPrincipal;
import com.project.goTruck.service.IdempotencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompanyPersistenceService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final FeatureRepository featureRepository;
    private final CompanyFeatureRepository companyFeatureRepository;
    private final UserFeatureRepository userFeatureRepository;
    private final CompanyProvisioningOutboxRepository outboxRepository;
    private final IdempotencyService idempotencyService;

    /**
     * Persists the complete local company aggregate AND the provisioning
     * outbox row in one atomic database transaction:
     *
     *   company + owner user + company features + user features
     *   + idempotency record (when a key was provided)
     *   + PENDING outbox event for the Keycloak company_id update
     *
     * Either everything commits together or nothing does — the classic
     * transactional outbox guarantee. The remote Keycloak call is never
     * part of this transaction; it is performed asynchronously by the
     * outbox worker, which claims the PENDING row via a lease.
     */
    @Transactional
    public Company createCompanyWithOwner(
            CreateCompanyRequest request,
            CurrentUserPrincipal currentUser,
            String idempotencyKey
    ) {
        Company company = companyRepository.save(Company.builder()
                .name(request.name().trim())
                .website(request.website())
                .email(request.email())
                .location(request.location())
                .city(request.city())
                .country(request.country())
                .build());

        User owner = userRepository.save(User.builder()
                .keycloakId(currentUser.keycloakId())
                .company(company)
                .owner(true)
                .superAdmin(false)
                .firstName(currentUser.firstName())
                .lastName(currentUser.lastName())
                .username(currentUser.username())
                .email(currentUser.email())
                .build());

        for (Feature feature : resolveDefaultFeatures()) {
            companyFeatureRepository.save(CompanyFeature.builder()
                    .company(company)
                    .feature(feature)
                    .build());

            userFeatureRepository.save(UserFeature.builder()
                    .user(owner)
                    .feature(feature)
                    .build());
        }

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            // Propagation.REQUIRED: joins this transaction, so the
            // idempotency record commits atomically with the aggregate.
            idempotencyService.storeResult(
                    idempotencyKey,
                    currentUser.keycloakId(),
                    company.getId()
            );
        }

        outboxRepository.save(CompanyProvisioningOutbox.newPendingEvent(
                company.getId(),
                currentUser.keycloakId(),
                Instant.now()
        ));

        return company;
    }

    private List<Feature> resolveDefaultFeatures() {
        List<Feature> allFeatures = featureRepository.findAll();

        // Collect feature names as FeatureRoleEnum directly (since name is an enum)
        List<FeatureRoleEnum> featureNames = allFeatures.stream()
                .map(Feature::getName)
                .toList();

        // Verify all required features exist in the database
        for (FeatureRoleEnum requiredFeature : FeatureRoleEnum.values()) {
            if (!featureNames.contains(requiredFeature)) {
                throw new IllegalStateException(
                        "Required feature not found in database: " + requiredFeature.name()
                );
            }
        }

        // Return all features from the database
        return allFeatures;
    }
}
