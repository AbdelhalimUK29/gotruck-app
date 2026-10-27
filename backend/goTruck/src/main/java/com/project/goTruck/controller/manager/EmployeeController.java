package com.project.goTruck.controller.manager;

import com.project.goTruck.dto.employee.CreateEmployeeRequest;
import com.project.goTruck.dto.employee.EmployeeDetailResponse;
import com.project.goTruck.dto.employee.EmployeeFilterDTO;
import com.project.goTruck.dto.employee.EmployeeResponse;
import com.project.goTruck.dto.employee.UpdateEmployeeRequest;
import com.project.goTruck.security.argumentresolver.SessionCompanyId;
import com.project.goTruck.security.current.CurrentUserPrincipal;
import com.project.goTruck.security.current.CurrentUserService;
import com.project.goTruck.service.employee.EmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/manager/employees")
@RequiredArgsConstructor
@Slf4j
public class EmployeeController {

    private final EmployeeService employeeService;
    private final CurrentUserService currentUserService;

    /**
     * Creates a new employee for the current user's company.
     *
     * The company is derived from the authenticated user's session via @SessionCompanyId.
     * Only company owners or super admins can create employees.
     *
     * @param request    Employee details (username, email, name, password)
     * @param companyId  Company ID from session (injected by SessionCompanyIdArgumentResolver)
     * @return Created employee with assigned features
     */
    @PostMapping
    @PreAuthorize("hasPermission(null,T(com.project.goTruck.feature.FeatureRoleEnum).ADMIN)")
    public ResponseEntity<EmployeeResponse> createEmployee(
            @Valid @RequestBody CreateEmployeeRequest request,
            @SessionCompanyId Long companyId
    ) {
        log.info("Creating employee for company: {}", companyId);
        EmployeeResponse response = employeeService.createEmployee(request, companyId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves paginated list of employees for the current company.
     *
     * Filters applied automatically:
     * - Only employees from the current company (via @SessionCompanyId)
     * - Excludes owners (owner = false) — unless excludeUsers=false (default)
     * - Excludes the current authenticated user — unless excludeUsers=true
     * - Optional fullname search (case-insensitive partial match)
     *
     * Requires authentication only (no admin required).
     *
     * Query parameters:
     * - fullname: Partial match on firstName + lastName (optional)
     * - excludeUsers: Set to true to return all users including owners and self (optional, default: false)
     * - page: Page number, 0-based (default: 0)
     * - size: Page size (default: 20, max: 100)
     * - sort: Sort field and direction (e.g., "firstName,asc" or "lastName,desc")
     *
     * @param filter    Filter criteria from query parameters
     * @param companyId Company ID from session (injected by SessionCompanyIdArgumentResolver)
     * @return Page of EmployeeResponse with pagination metadata
     */
    @GetMapping
    public ResponseEntity<Page<EmployeeResponse>> getEmployees(
            @Valid @ModelAttribute EmployeeFilterDTO filter,
            @SessionCompanyId Long companyId
    ) {
        log.info("Fetching employees for company: {} with filter: {}", companyId, filter);
        Page<EmployeeResponse> employees = employeeService.getEmployees(filter, companyId);
        return ResponseEntity.ok(employees);
    }

    /**
     * Retrieves detailed information for a specific employee including their user features.
     *
     * Path parameter:
     * - employeeId: Employee's local database ID
     *
     * Validations (enforced by service):
     * - Employee must belong to the current company (@SessionCompanyId)
     * - Employee must NOT be an owner (owner = false)
     * - Employee must NOT be the current authenticated user
     *
     * Admin access required to view detailed employee information.
     *
     * @param employeeId Employee ID from path
     * @param companyId  Company ID from session (injected by SessionCompanyIdArgumentResolver)
     * @return EmployeeDetailResponse with user features
     */
    @GetMapping("/{employeeId}")
    @PreAuthorize("hasPermission(null,T(com.project.goTruck.feature.FeatureRoleEnum).ADMIN)")
    public ResponseEntity<EmployeeDetailResponse> getEmployeeDetail(
            @PathVariable Long employeeId,
            @SessionCompanyId Long companyId
    ) {
        log.info("Fetching employee detail: {} for company: {}", employeeId, companyId);
        CurrentUserPrincipal currentUser = currentUserService.currentUser();
        EmployeeDetailResponse employee = employeeService.getEmployeeDetail(
                employeeId, companyId, currentUser.keycloakId());
        return ResponseEntity.ok(employee);
    }

    /**
     * Updates specific fields of an employee: superAdmin status and/or features.
     *
     * The company is derived from the authenticated user's session via @SessionCompanyId.
     * Only company owners or super admins can update employees.
     * Cannot update owner accounts or the current authenticated user.
     *
     * @param employeeId Employee's local database ID
     * @param request    Fields to update (superAdmin and/or features)
     * @param companyId  Company ID from session (injected by SessionCompanyIdArgumentResolver)
     * @return Updated employee with features
     */
    @PatchMapping("/{employeeId}")
    @PreAuthorize("hasPermission(null,T(com.project.goTruck.feature.FeatureRoleEnum).ADMIN)")
    public ResponseEntity<EmployeeDetailResponse> updateEmployee(
            @PathVariable Long employeeId,
            @Valid @RequestBody UpdateEmployeeRequest request,
            @SessionCompanyId Long companyId
    ) {
        log.info("Updating employee: {} for company: {}", employeeId, companyId);
        CurrentUserPrincipal currentUser = currentUserService.currentUser();
        EmployeeDetailResponse updated = employeeService.updateEmployee(
                employeeId, companyId, currentUser.keycloakId(), request);
        return ResponseEntity.ok(updated);
    }
}