package com.project.goTruck.service.employee;

import com.project.goTruck.dto.employee.CreateEmployeeRequest;
import com.project.goTruck.entity.Company;
import com.project.goTruck.entity.CompanyFeature;
import com.project.goTruck.entity.Feature;
import com.project.goTruck.entity.UserFeature;
import com.project.goTruck.entity.user.User;
import com.project.goTruck.feature.FeatureRoleEnum;
import com.project.goTruck.mapper.employee.EmployeeMapper;
import com.project.goTruck.repository.CompanyFeatureRepository;
import com.project.goTruck.repository.UserFeatureRepository;
import com.project.goTruck.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmployeePersistenceService {

    private final UserRepository userRepository;
    private final CompanyFeatureRepository companyFeatureRepository;
    private final UserFeatureRepository userFeatureRepository;
    private final EmployeeMapper employeeMapper;

    /**
     * Employee default features - only grant these if company has them
     */
    private static final Set<FeatureRoleEnum> EMPLOYEE_DEFAULT_FEATURES = Set.of(
            FeatureRoleEnum.RESERVATION,
            FeatureRoleEnum.CLIENTS,
            FeatureRoleEnum.TRUCKS,
            FeatureRoleEnum.LONG_TIME_RENTAL
    );

    /**
     * Persists a new employee user and assigns a subset of the company's features.
     * Only grants RESERVATION, CLIENTS, TRUCKS, LONG_TIME_RENTAL if the company has them.
     *
     * @param keycloakId  Keycloak user ID from Keycloak creation
     * @param request     Employee creation request
     * @param company     Company the employee belongs to (from @SessionCompanyId)
     * @return the persisted User entity with UserFeatures loaded
     */
    @Transactional
    public User createEmployeeWithCompanyFeatures(
            UUID keycloakId,
            CreateEmployeeRequest request,
            Company company
    ) {
        // 1. Map request to User entity using MapStruct
        User employee = employeeMapper.toEntity(request);
        employee.setKeycloakId(keycloakId);
        employee.setCompany(company);

        employee = userRepository.save(employee);

        // 2. Resolve company's available features using companyId
        List<CompanyFeature> companyFeatures = companyFeatureRepository.findByCompanyId(company.getId());

        // 3. Create UserFeature for each company feature that is in the employee default set
        Set<String> employeeDefaultFeatureNames = EMPLOYEE_DEFAULT_FEATURES.stream()
                .map(Enum::name)
                .collect(Collectors.toSet());

        for (CompanyFeature companyFeature : companyFeatures) {
            Feature feature = companyFeature.getFeature();
            // Only grant if this feature is in the employee default features set
            if (employeeDefaultFeatureNames.contains(feature.getName().name())) {
                userFeatureRepository.save(UserFeature.builder()
                        .user(employee)
                        .feature(feature)
                        .build());
            }
        }

        return employee;
    }
}