package com.project.goTruck.service.company;

import com.project.goTruck.config.MediaLogoProperties;
import com.project.goTruck.dto.company.CompanyFeatureResponse;
import com.project.goTruck.dto.company.CompanyLogoUploadResponse;
import com.project.goTruck.dto.company.CompanyResponse;
import com.project.goTruck.dto.company.CreateCompanyRequest;
import com.project.goTruck.dto.company.UpdateCompanyRequest;
import com.project.goTruck.entity.Company;
import com.project.goTruck.entity.CompanyFeature;
import com.project.goTruck.entity.CompanyProvisioningOutbox;
import com.project.goTruck.entity.user.User;
import com.project.goTruck.exception.CompanyAlreadyExistsException;
import com.project.goTruck.exception.CompanyNotFoundException;
import com.project.goTruck.mapper.company.CompanyFeatureMapper;
import com.project.goTruck.media.messaging.LogoProcessingPublisher;
import com.project.goTruck.media.processing.ImageValidation;
import com.project.goTruck.media.storage.LogoUploadGuardStore;
import com.project.goTruck.media.storage.MinioStorageService;
import com.project.goTruck.repository.CompanyFeatureRepository;
import com.project.goTruck.repository.CompanyProvisioningOutboxRepository;
import com.project.goTruck.repository.CompanyRepository;
import com.project.goTruck.repository.UserRepository;
import com.project.goTruck.security.current.CurrentUserPrincipal;
import com.project.goTruck.security.current.CurrentUserService;
import com.project.goTruck.service.IdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final CompanyProvisioningOutboxRepository outboxRepository;
    private final CompanyPersistenceService companyPersistenceService;
    private final IdempotencyService idempotencyService;
    private final MediaLogoProperties mediaLogoProperties;
    private final MinioStorageService minioStorageService;
    private final LogoUploadGuardStore logoUploadGuardStore;
    private final LogoProcessingPublisher logoProcessingPublisher;
    private final CompanyFeatureRepository companyFeatureRepository;
    private final CompanyFeatureMapper companyFeatureMapper;

    /**
     * Creates a company without ever touching Keycloak on the request
     * path. The local aggregate (company, owner, features), the
     * idempotency record, and a PENDING provisioning outbox event are
     * committed in one database transaction; the Keycloak company_id
     * update is applied asynchronously by the outbox worker with
     * retries, backoff, and compensation.
     *
     * The response therefore always reports provisioningStatus=PENDING
     * for fresh creations; clients poll getProvisioningStatus() to
     * observe transitions to a terminal state.
     */
    public CompanyResponse createCompany(
            CreateCompanyRequest request,
            String idempotencyKey
    ) {
        CurrentUserPrincipal currentUser = currentUserService.currentUser();
        UUID keycloakId = currentUser.keycloakId();

        log.info("Company creation requested by user: {}", keycloakId);

        Optional<CompanyResponse> existingResult =
                getExistingResult(idempotencyKey, keycloakId);
        if (existingResult.isPresent()) {
            log.info("Idempotency hit for key: {}", idempotencyKey);
            return existingResult.get();
        }

        checkUserNotInCompany(currentUser, keycloakId);

        Company company = companyPersistenceService.createCompanyWithOwner(
                request,
                currentUser,
                idempotencyKey
        );

        log.info(
                "Company created and provisioning scheduled: id={}, name={}, owner={}",
                company.getId(), company.getName(), keycloakId
        );

        return toResponse(company, ProvisioningStatusLocal.PENDING);
    }

    /**
     * Retrieves the company information for the current user's company by ID.
     *
     * Validates that the requested ID matches the authenticated user's company,
     * then returns the company data.
     *
     * @param companyId the company ID to retrieve
     * @return the company data
     * @throws CompanyNotFoundException if the company doesn't exist or access is denied
     */
    public CompanyResponse getCompanyById(Long companyId) {
        CurrentUserPrincipal currentUser = currentUserService.currentUser();
        Long userCompanyId = currentUser.companyId();

        log.info("get company for user: {}, companyId: {}", currentUser.keycloakId() ,userCompanyId);
        if (userCompanyId == null || !userCompanyId.equals(companyId)) {
            throw new CompanyNotFoundException("Company not found or access denied");
        }
        
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new CompanyNotFoundException("Company not found: " + companyId));
        
        // Return company data without provisioning status
        return toResponse(company, null);
    }


    /**
     * Accepts a company-logo upload for asynchronous processing.
     *
     * The request path only validates, stores the original in the
     * private temp bucket, records the stale-job guard, and publishes
     * a RabbitMQ event ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â it never waits for image processing. The
     * worker (LogoProcessingWorker) does the heavy lifting and is the
     * only component that updates companies.logo.
     *
     * Failure handling (no half-broken state is left behind):
     *  A. invalid image        -> InvalidImageException (400), nothing stored
     *  B. temp upload fails    -> StorageException (502), no guard written
     *  C. guard write fails    -> temp object deleted, error propagated
     *  D. publish fails        -> temp object deleted, error propagated
     *
     * The company's current logo is never touched here.
     */
    public CompanyLogoUploadResponse uploadCompanyLogo(MultipartFile file) {
        CurrentUserPrincipal currentUser = currentUserService.currentUser();
        UUID keycloakId = currentUser.keycloakId();

        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated user has no local account."));

        Company company = user.getCompany();
        if (company == null) {
            throw new IllegalStateException(
                    "Authenticated user does not belong to a company.");
        }
        Long companyId = company.getId();

        UUID uploadId = UUID.randomUUID();
        log.info("Logo upload started: uploadId={}, companyId={}",
                uploadId, companyId);

        byte[] data = readBytes(file);

        String contentType = ImageValidation.validate(
                data,
                mediaLogoProperties.getMaxUploadSize().toBytes(),
                mediaLogoProperties.getMaxWidth(),
                mediaLogoProperties.getMaxHeight(),
                allowedFormats());
        log.info("Logo validation succeeded: uploadId={}, companyId={}, "
                + "detectedType={}", uploadId, companyId, contentType);

        String tempObjectKey =
                LogoUploadGuardStore.tempObjectKey(companyId, uploadId);

        // B: temp upload failure -> nothing else has happened yet.
        minioStorageService.uploadTemp(tempObjectKey, data, contentType);
        log.info("Temp object uploaded: uploadId={}, companyId={}, key={}",
                uploadId, companyId, tempObjectKey);

        // C: guard write failure -> remove the orphaned temp object.
        try {
            logoUploadGuardStore.markLatest(uploadId, companyId);
        } catch (RuntimeException e) {
            safeDeleteTemp(tempObjectKey, uploadId, companyId);
            throw e;
        }

        // D: publish failure -> remove the orphaned temp object.
        try {
            logoProcessingPublisher.publish(
                    uploadId, companyId, tempObjectKey);
        } catch (RuntimeException e) {
            safeDeleteTemp(tempObjectKey, uploadId, companyId);
            throw e;
        }

        log.info("Logo upload accepted for processing: uploadId={}, "
                + "companyId={}", uploadId, companyId);

        return new CompanyLogoUploadResponse(uploadId);
    }

    /**
     * Updates the company information for the current user's company.
     *
     * Validates that the request ID matches the authenticated user's company,
     * then applies partial updates to the company fields (only non-null values).
     * The logo is excluded from this endpoint and handled separately via /logo.
     *
     * @param request the update request containing company ID and optional fields
     * @return the updated company data
     * @throws CompanyNotFoundException if the company doesn't exist or access is denied
     */
    @Transactional
    public CompanyResponse updateCompany(UpdateCompanyRequest request) {
        CurrentUserPrincipal currentUser = currentUserService.currentUser();
        Long userCompanyId = currentUser.companyId();

        if (userCompanyId == null || !userCompanyId.equals(request.id())) {
            throw new CompanyNotFoundException("Company not found or access denied");
        }

        Company company = companyRepository.findById(request.id())
                .orElseThrow(() -> new CompanyNotFoundException("Company not found: " + request.id()));

        // Partial update - only set non-null values
        if (request.name() != null && !request.name().isBlank()) {
            company.setName(request.name().trim());
        }
        if (request.website() != null) {
            company.setWebsite(request.website());
        }
        if (request.email() != null) {
            company.setEmail(request.email());
        }
        if (request.location() != null) {
            company.setLocation(request.location());
        }
        if (request.city() != null) {
            company.setCity(request.city());
        }
        if (request.country() != null) {
            company.setCountry(request.country());
        }

        companyRepository.save(company);

        log.info("Company updated: id={}, name={}", company.getId(), company.getName());

        // Return updated company data without provisioning status
        return toResponse(company, null);
    }

    private byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new com.project.goTruck.exception.InvalidImageException(
                    "The uploaded file is empty.");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new com.project.goTruck.exception.InvalidImageException(
                    "The uploaded file could not be read.");
        }
    }

    private Set<String> allowedFormats() {
        Set<String> formats = new HashSet<>();
        for (String format : mediaLogoProperties.getAllowedFormats()) {
            formats.add(format.toUpperCase(Locale.ROOT));
        }
        return formats;
    }

    private void safeDeleteTemp(
            String tempObjectKey,
            UUID uploadId,
            Long companyId
    ) {
        try {
            minioStorageService.deleteTemp(tempObjectKey);
        } catch (RuntimeException cleanupError) {
            // The abandoned-temp cleanup job is the safety net.
            log.warn("Failed to clean up temp object after upload failure: "
                            + "uploadId={}, companyId={}, key={}",
                    uploadId, companyId, tempObjectKey, cleanupError);
        }
    }

    /**
     * Returns the live provisioning state of the current user's most
     * recent company creation attempt, for client polling.
     *
     * Empty when the user never created a company. After compensation
     * the company row is gone but the outbox row survives, so the
     * terminal status (e.g. COMPENSATED) is still reported with the
     * company fields left null.
     */
    public Optional<CompanyResponse> getProvisioningStatus() {
        CurrentUserPrincipal currentUser = currentUserService.currentUser();
        UUID keycloakId = currentUser.keycloakId();

        return outboxRepository
                .findTopByKeycloakUserIdAndEventTypeOrderByIdDesc(
                        keycloakId,
                        CompanyProvisioningOutbox.EVENT_UPDATE_KEYCLOAK_COMPANY_ID
                )
                .map(event -> companyRepository
                        .findById(event.getCompanyId())
                        .map(company -> toResponse(company, event.getStatus().name()))
                        .orElseGet(() -> toDeletedCompanyResponse(event)));
    }

    private Optional<CompanyResponse> getExistingResult(
            String idempotencyKey,
            UUID keycloakId
    ) {
        if (!hasText(idempotencyKey)) {
            return Optional.empty();
        }
        return idempotencyService
                .findCompanyId(idempotencyKey, keycloakId)
                .flatMap(this::toCurrentStateResponse);
    }

    /**
     * Builds the replay response from the CURRENT outbox state rather
     * than a stored snapshot, so a retried request observes the same
     * progression (PENDING -> ... -> terminal) as the original one.
     */
    private Optional<CompanyResponse> toCurrentStateResponse(Long companyId) {
        return outboxRepository
                .findByCompanyIdAndEventType(
                        companyId,
                        CompanyProvisioningOutbox.EVENT_UPDATE_KEYCLOAK_COMPANY_ID
                )
                .flatMap(event -> companyRepository
                        .findById(companyId)
                        .map(company ->
                                toResponse(company, event.getStatus().name())
                        ));
    }

    private void checkUserNotInCompany(
            CurrentUserPrincipal currentUser,
            UUID keycloakId
    ) {
        if (currentUser.companyId() != null) {
            throw new CompanyAlreadyExistsException(
                    "You already belong to a company (found in authentication token)."
            );
        }

        userRepository.findByKeycloakId(keycloakId).ifPresent(user -> {
            if (user.getCompany() != null) {
                throw new CompanyAlreadyExistsException(
                        "You already belong to a company."
                );
            }
        });
    }

    private CompanyResponse toDeletedCompanyResponse(
            CompanyProvisioningOutbox event
    ) {
        return new CompanyResponse(
                event.getCompanyId(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                event.getStatus().name()
        );
    }

    private CompanyResponse toResponse(Company company, String status) {
        return new CompanyResponse(
                company.getId(),
                company.getName(),
                company.getWebsite(),
                company.getEmail(),
                company.getLocation(),
                company.getCity(),
                company.getCountry(),
                company.getLogo(),
                status
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Retrieves all features enabled for the given company.
     *
     * Uses a single query with JOIN FETCH to avoid N+1 when accessing feature details.
     *
     * @param companyId the company ID
     * @return list of company feature responses (empty list if none)
     */
    public List<CompanyFeatureResponse> getCompanyFeatures(Long companyId) {
        List<CompanyFeature> companyFeatures =
                companyFeatureRepository.findByCompanyIdWithFeatures(companyId);
        return companyFeatures.stream()
                .map(companyFeatureMapper::toResponse)
                .toList();
    }

    /**
     * Updates the company's logo to the new permanent object key.
     *
     * This method is called by the LogoProcessingWorker AFTER the
     * processed image has been successfully uploaded to the public
     * bucket. It runs in a transaction to ensure the logo reference
     * is updated atomically.
     *
     * @param companyId the company whose logo is being updated
     * @param newLogoKey the permanent object key in the public bucket
     * @return the previous logo key (null if no previous logo), or
     *         the same key if the update is a no-op (duplicate)
     */
    @Transactional
    public String updateCompanyLogo(Long companyId, String newLogoKey) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalStateException(
                        "Company not found: " + companyId));

        String oldLogoKey = company.getLogo();

        // No-op if the new key is the same as the current one (duplicate/redelivery)
        if (newLogoKey != null && newLogoKey.equals(oldLogoKey)) {
            log.info("Logo update is a no-op (duplicate): companyId={}, key={}",
                    companyId, newLogoKey);
            return oldLogoKey;
        }

        company.setLogo(newLogoKey);
        companyRepository.save(company);

        log.info("Company logo updated: companyId={}, oldKey={}, newKey={}",
                companyId, oldLogoKey, newLogoKey);

        return oldLogoKey;
    }

    /** Local alias keeping the PENDING literal out of call sites. */
    private static final class ProvisioningStatusLocal {
        static final String PENDING =
                com.project.goTruck.entity.ProvisioningStatus.PENDING.name();
    }
}



