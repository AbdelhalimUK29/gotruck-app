package com.project.goTruck.service.employee;

import com.project.goTruck.dto.employee.CreateEmployeeRequest;
import com.project.goTruck.dto.employee.EmployeeDetailResponse;
import com.project.goTruck.dto.employee.EmployeeFilterDTO;
import com.project.goTruck.dto.employee.EmployeeResponse;
import com.project.goTruck.dto.employee.UpdateEmployeeRequest;
import com.project.goTruck.entity.Company;
import com.project.goTruck.entity.CompanyFeature;
import com.project.goTruck.entity.Feature;
import com.project.goTruck.entity.UserFeature;
import com.project.goTruck.entity.user.User;
import com.project.goTruck.exception.CompanyNotFoundException;
import com.project.goTruck.exception.EmployeeNotFoundException;
import com.project.goTruck.exception.FeatureNotEnabledForCompanyException;
import com.project.goTruck.exception.KeycloakUserCreationException;
import com.project.goTruck.exception.KeycloakUserDeletionException;
import com.project.goTruck.feature.FeatureRoleEnum;
import com.project.goTruck.keycloak.KeycloakAdminClient;
import com.project.goTruck.mapper.employee.EmployeeMapper;
import com.project.goTruck.repository.CompanyFeatureRepository;
import com.project.goTruck.repository.CompanyRepository;
import com.project.goTruck.repository.FeatureRepository;
import com.project.goTruck.repository.UserFeatureRepository;
import com.project.goTruck.repository.UserRepository;
import com.project.goTruck.security.current.CurrentUserPrincipal;
import com.project.goTruck.security.current.CurrentUserService;
import com.project.goTruck.specification.EmployeeSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final KeycloakAdminClient keycloakAdminClient;
    private final EmployeePersistenceService persistenceService;
    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final CompanyFeatureRepository companyFeatureRepository;
    private final UserFeatureRepository userFeatureRepository;
    private final FeatureRepository featureRepository;
    private final EmployeeMapper employeeMapper;

    /**
     * Creates a new employee for the current user's company.
     *
     * Flow:
     * 1. Validate requester is company owner OR superAdmin
     * 2. Create user in Keycloak with company_id attribute (external transaction)
     * 3. Persist user + all company features in local DB (atomic transaction)
     * 4. If DB fails, compensate by deleting Keycloak user
     *
     * @param request   Employee creation request
     * @param companyId Company ID from @SessionCompanyId annotation
     * @return EmployeeResponse
     */
    public EmployeeResponse createEmployee(CreateEmployeeRequest request, Long companyId) {
        CurrentUserPrincipal currentUser = currentUserService.currentUser();
        UUID managerKeycloakId = currentUser.keycloakId();

        log.info("Employee creation requested by user: {} for company: {}", managerKeycloakId, companyId);

        // 1. Validate company access via SessionCompanyId
        if (!companyId.equals(currentUser.companyId())) {
            throw new SecurityException("Unauthorized for this company");
        }

        // 2. Load and verify company exists
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new CompanyNotFoundException("Company not found: " + companyId));

        // 3. Verify requester is owner OR superAdmin of THIS company
        User manager = userRepository.findByKeycloakId(managerKeycloakId)
                .orElseThrow(() -> new IllegalStateException("Manager not found in local DB"));

        if ((!manager.isOwner() && !manager.isSuperAdmin())) {
            throw new SecurityException("Only company owner or super admin can create employees");
        }

        // 4. TRANSACTION 1: Create user in Keycloak with company_id (external, separate)
        UUID keycloakUserId;
        try {
            keycloakUserId = keycloakAdminClient.createUser(
                    request.username(),
                    request.email(),
                    request.firstName(),
                    request.lastName(),
                    request.password(),
                    companyId.toString()
            );
            log.info("Keycloak user created: {} for company: {}", keycloakUserId, companyId);
        } catch (KeycloakUserCreationException e) {
            if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                throw new IllegalArgumentException("Username or email already exists in Keycloak");
            }
            log.error("Failed to create user in Keycloak", e);
            throw e;
        } catch (Exception e) {
            log.error("Failed to create user in Keycloak", e);
            throw new KeycloakUserCreationException("Failed to create user in Keycloak", e);
        }

        // 5. TRANSACTION 2: Persist locally with all company features (atomic)
        User employee;
        try {
            employee = persistenceService.createEmployeeWithCompanyFeatures(
                    keycloakUserId,
                    request,
                    company
            );
            log.info("Employee persisted locally: id={}, keycloakId={}, company={}", employee.getId(), keycloakUserId, companyId);
        } catch (DataIntegrityViolationException e) {
            // Compensation: Delete Keycloak user if DB constraint fails
            compensateKeycloakUser(keycloakUserId, "DB constraint violation");
            throw new IllegalArgumentException("Username or email already exists in this company", e);
        } catch (Exception e) {
            // Compensation: Delete Keycloak user on any DB failure
            compensateKeycloakUser(keycloakUserId, e.getMessage());
            throw e;
        }

        // 6. Build and return response using MapStruct
        return employeeMapper.toResponse(employee);
    }

    /**
     * Retrieves paginated list of employees for the current company.
     *
     * Filters applied:
     * - Only employees from the current company (via @SessionCompanyId)
     * - Excludes owners (owner = false)
     * - Excludes super admins (superAdmin = false)
     * - Excludes the current authenticated user
     * - Optional fullname search (case-insensitive partial match)
     *
     * @param filter    Filter criteria (fullname, page, size, sort)
     * @param companyId Company ID from @SessionCompanyId
     * @return Page of EmployeeResponse with pagination metadata
     */
    public Page<EmployeeResponse> getEmployees(EmployeeFilterDTO filter, Long companyId) {
        CurrentUserPrincipal currentUser = currentUserService.currentUser();
        UUID currentUserKeycloakId = currentUser.keycloakId();

        log.info("Fetching employees for company: {} by user: {}", companyId, currentUserKeycloakId);

        // Build specification with all filters
        var specification = EmployeeSpecification.build(filter, companyId, currentUserKeycloakId);

        // Get pageable from filter
        Pageable pageable = filter.toPageable();

        // Execute query with specification and pagination
        Page<User> userPage = userRepository.findAll(specification, pageable);

        // Map to response DTOs preserving pagination
        return employeeMapper.toResponsePage(userPage);
    }

    /**
     * Best-effort compensation: delete Keycloak user if local persistence fails.
     * Logs errors but doesn't throw - original exception takes precedence.
     */
    private void compensateKeycloakUser(UUID keycloakUserId, String reason) {
        try {
            keycloakAdminClient.deleteUser(keycloakUserId);
            log.warn("Compensated Keycloak user deletion: {} (reason: {})", keycloakUserId, reason);
        } catch (KeycloakUserDeletionException e) {
            // Log but don't throw - original DB exception is more important
            log.error("Failed to compensate Keycloak user deletion: {} (reason: {})",
                    keycloakUserId, reason, e);
        } catch (Exception e) {
            log.error("Unexpected error during Keycloak compensation: {} (reason: {})",
                    keycloakUserId, reason, e);
        }
    }

    /**
     * Retrieves detailed information for a specific employee including their user features.
     *
     * Validations:
     * - Employee must belong to the current company (@SessionCompanyId)
     * - Employee must NOT be an owner (owner = false)
     * - Employee must NOT be the current authenticated user
     *
     * @param employeeId           Employee's local DB ID
     * @param companyId            Company ID from @SessionCompanyId
     * @param currentUserKeycloakId Current user's Keycloak ID (for self-exclusion)
     * @return EmployeeDetailResponse with user features
     * @throws EmployeeNotFoundException if employee doesn't exist or fails validations
     */
    public EmployeeDetailResponse getEmployeeDetail(Long employeeId, Long companyId, UUID currentUserKeycloakId) {
        // 1. Fetch employee with userFeatures + feature in single query (fetch join)
        User employee = userRepository.findByIdAndCompanyIdWithUserFeatures(employeeId, companyId)
                .orElseThrow(() -> new EmployeeNotFoundException("Employee not found: " + employeeId));

        // 2. Validate: not owner
        if (employee.isOwner()) {
            throw new SecurityException("Cannot access owner account");
        }

        // 3. Validate: not current user
        if (employee.getKeycloakId().equals(currentUserKeycloakId)) {
            throw new SecurityException("Cannot access your own account via this endpoint");
        }

        // 4. Map to response (userFeatures + feature already loaded via fetch join)
        return employeeMapper.toDetailResponse(employee);
    }

    /**
     * Updates specific fields of an employee: superAdmin status and/or features.
     *
     * @param employeeId           Employee's local DB ID
     * @param companyId            Company ID from @SessionCompanyId
     * @param currentUserKeycloakId Current user's Keycloak ID (for authorization)
     * @param request              Fields to update (superAdmin and/or features)
     * @return Updated employee with features
     * @throws EmployeeNotFoundException if employee doesn't exist or fails validations
     * @throws SecurityException if requester is not authorized or tries to modify owner/self
     * @throws FeatureNotEnabledForCompanyException if requested features not enabled for company
     * @throws IllegalArgumentException if no fields provided
     */
    @Transactional
    public EmployeeDetailResponse updateEmployee(
            Long employeeId,
            Long companyId,
            UUID currentUserKeycloakId,
            UpdateEmployeeRequest request
    ) {
        // 1. Validate request has changes
        if (!request.hasChanges()) {
            throw new IllegalArgumentException("At least one field (superAdmin or features) must be provided");
        }

        // 2. Load employee with validations (optimized - no features needed here)
        User employee = userRepository.findByIdAndCompanyId(employeeId, companyId)
                .orElseThrow(() -> new EmployeeNotFoundException("Employee not found: " + employeeId));

        // 3. Validate: not owner
        if (employee.isOwner()) {
            throw new SecurityException("Cannot modify owner account");
        }

        // 4. Validate: not current user
        if (employee.getKeycloakId().equals(currentUserKeycloakId)) {
            throw new SecurityException("Cannot modify your own account via this endpoint");
        }

        // 5. Validate requester is owner OR superAdmin
        validateRequesterAuthorization(companyId, currentUserKeycloakId);

        // 6. Update superAdmin if provided
        if (request.superAdmin() != null) {
            employee.setSuperAdmin(request.superAdmin());
            employee = userRepository.save(employee);
            log.info("Updated superAdmin for employee {} to: {}", employeeId, request.superAdmin());
        }

        // 7. Update features if provided
        if (request.features() != null) {
            updateEmployeeFeatures(companyId, employeeId, employee, request.features());
            log.info("Updated features for employee {}", employeeId);
        }

        // 8. Return updated employee with fresh features
        User updated = userRepository.findByIdAndCompanyIdWithUserFeatures(employeeId, companyId)
                .orElseThrow(() -> new IllegalStateException("Employee not found after update: " + employeeId));
        return employeeMapper.toDetailResponse(updated);
    }

    /**
     * Updates employee features - copied logic from UserService to make EmployeeService self-contained.
     */
    private void updateEmployeeFeatures(Long companyId, Long userId, User targetUser, Set<FeatureRoleEnum> requestedFeatures) {
        // Load company's enabled features
        Set<FeatureRoleEnum> companyFeatures = getCompanyFeatures(companyId);

        // Validate all requested features are enabled for the company
        validateFeaturesEnabledForCompany(requestedFeatures, companyFeatures);

        // Load current user features
        Set<FeatureRoleEnum> currentFeatures = getUserCurrentFeatures(userId);

        // Calculate diff
        Set<FeatureRoleEnum> toRemove = new HashSet<>(currentFeatures);
        toRemove.removeAll(requestedFeatures);

        Set<FeatureRoleEnum> toAdd = new HashSet<>(requestedFeatures);
        toAdd.removeAll(currentFeatures);

        // Apply removals
        if (!toRemove.isEmpty()) {
            userFeatureRepository.deleteByUserIdAndFeatureNames(userId, toRemove);
            log.debug("Removed features for user {}: {}", userId, toRemove);
        }

        // Apply additions
        if (!toAdd.isEmpty()) {
            addFeaturesToUser(targetUser, toAdd);
            log.debug("Added features for user {}: {}", userId, toAdd);
        }
    }

    /**
     * Loads all features enabled for the company.
     */
    private Set<FeatureRoleEnum> getCompanyFeatures(Long companyId) {
        List<CompanyFeature> companyFeatures = companyFeatureRepository.findByCompanyId(companyId);
        return companyFeatures.stream()
                .map(cf -> cf.getFeature().getName())
                .collect(Collectors.toSet());
    }

    /**
     * Validates that all requested features are enabled for the company.
     */
    private void validateFeaturesEnabledForCompany(Set<FeatureRoleEnum> requestedFeatures, Set<FeatureRoleEnum> companyFeatures) {
        Set<FeatureRoleEnum> notEnabled = new HashSet<>(requestedFeatures);
        notEnabled.removeAll(companyFeatures);

        if (!notEnabled.isEmpty()) {
            Set<String> notEnabledNames = notEnabled.stream()
                    .map(Enum::name)
                    .collect(Collectors.toSet());
            throw new FeatureNotEnabledForCompanyException(
                    "The following features are not enabled for this company: " + notEnabledNames,
                    notEnabledNames
            );
        }
    }

    /**
     * Loads current features assigned to the user.
     */
    private Set<FeatureRoleEnum> getUserCurrentFeatures(Long userId) {
        List<UserFeature> userFeatures = userFeatureRepository.findByUserId(userId);
        return userFeatures.stream()
                .map(uf -> uf.getFeature().getName())
                .collect(Collectors.toSet());
    }

    /**
     * Adds features to user by creating UserFeature entities.
     */
    private void addFeaturesToUser(User user, Set<FeatureRoleEnum> featuresToAdd) {
        for (FeatureRoleEnum featureName : featuresToAdd) {
            Feature feature = featureRepository.findByName(featureName)
                    .orElseThrow(() -> new IllegalStateException("Feature not found in system: " + featureName));

            UserFeature userFeature = UserFeature.builder()
                    .user(user)
                    .feature(feature)
                    .build();

            userFeatureRepository.save(userFeature);
        }
    }

    /**
     * Validates that the current authenticated user is owner OR superAdmin of the company.
     */
    private void validateRequesterAuthorization(Long companyId, UUID currentUserKeycloakId) {
        User manager = userRepository.findByKeycloakId(currentUserKeycloakId)
                .orElseThrow(() -> new IllegalStateException("Manager not found in local DB"));

        if (!manager.isOwner() && !manager.isSuperAdmin()) {
            throw new SecurityException("Only company owner or super admin can update employees");
        }
    }
}