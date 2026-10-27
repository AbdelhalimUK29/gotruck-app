package com.project.goTruck.controller.manager;
import com.project.goTruck.dto.company.CompanyFeatureResponse;
import com.project.goTruck.dto.company.CompanyLogoUploadResponse;
import com.project.goTruck.dto.company.CompanyResponse;
import com.project.goTruck.dto.company.CreateCompanyRequest;
import com.project.goTruck.dto.company.UpdateCompanyRequest;
import com.project.goTruck.security.argumentresolver.SessionCompanyId;
import com.project.goTruck.service.company.CompanyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/manager/companies")
@RequiredArgsConstructor
@Slf4j
public class CompanyController {

    private final CompanyService companyService;

    /**
     * Creates a company. The response is returned as soon as the local
     * aggregate and the provisioning outbox event are committed ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â the
     * Keycloak company_id update happens asynchronously, so
     * provisioningStatus is PENDING at this point. Clients poll
     * GET /api/manager/companies/provisioning-status to track progress.
     */
    @PostMapping
    public ResponseEntity<CompanyResponse> createCompany(
            @Valid @RequestBody CreateCompanyRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        CompanyResponse response = companyService.createCompany(
                request, idempotencyKey
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    /**
     * Accepts a company-logo upload for asynchronous processing.
     *
     * Returns 202 Accepted immediately after validation, temp storage,
     * guard recording and RabbitMQ publication ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â the image is
     * processed by the worker, not on this request. The company's
     * current logo is untouched until processing succeeds.
     *
     * The company is derived from the authenticated user; a user can
     * only ever change their own company's logo.
     */
    @PostMapping(
            path = "/logo",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @PreAuthorize("hasPermission(null,T(com.project.goTruck.feature.FeatureRoleEnum).ADMIN)")
    public ResponseEntity<CompanyLogoUploadResponse> uploadCompanyLogo(
            @RequestParam("file") MultipartFile file
    ) {
        CompanyLogoUploadResponse response =
                companyService.uploadCompanyLogo(file);

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(response);
    }

    /**
     * Returns the live provisioning status of the current user's most
     * recent company creation attempt. 404 when the user never started
     * one; after compensation the company fields are null and only the
     * terminal status (e.g. COMPENSATED) is reported.
     */
    @GetMapping("/provisioning-status")
    public ResponseEntity<CompanyResponse> getProvisioningStatus() {
        Optional<CompanyResponse> status =
                companyService.getProvisioningStatus();

        return ResponseEntity
                .of(status);
    }

    /**
     * Updates the current user's company information.
     *
     * The request must include the company ID for validation.
     * Only non-null fields in the request will be updated.
     * Logo updates are handled separately via POST /logo.
     */
    @PutMapping
    @PreAuthorize("hasPermission(null,T(com.project.goTruck.feature.FeatureRoleEnum).ADMIN)")
    public ResponseEntity<CompanyResponse> updateCompany(
            @Valid @RequestBody UpdateCompanyRequest request
    ) {
        CompanyResponse response = companyService.updateCompany(request);

        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves all features enabled for the current user's company.
     *
     * The company is derived from the authenticated user's session via @SessionCompanyId.
     *
     * @param companyId Company ID from session (injected by SessionCompanyIdArgumentResolver)
     * @return List of enabled features with details
     */
    @GetMapping("/features")
    @PreAuthorize("hasPermission(null,T(com.project.goTruck.feature.FeatureRoleEnum).ADMIN)")
    public ResponseEntity<List<CompanyFeatureResponse>> getCompanyFeatures(
            @SessionCompanyId Long companyId
    ) {
        log.info("Fetching features for company: {}", companyId);
        List<CompanyFeatureResponse> features = companyService.getCompanyFeatures(companyId);
        return ResponseEntity.ok(features);
    }
    /**
     * Retrieves the company information for the current user's company by ID.
     *
     * The company ID must match the authenticated user's company for authorization.
     *
     * @param companyId the company ID to retrieve
     * @return the company data
     */
    @GetMapping("/{companyId}")
    public ResponseEntity<CompanyResponse> getCompanyById(
            @PathVariable Long companyId
    ) {
        CompanyResponse response = companyService.getCompanyById(companyId);
        return ResponseEntity.ok(response);
    }


}



