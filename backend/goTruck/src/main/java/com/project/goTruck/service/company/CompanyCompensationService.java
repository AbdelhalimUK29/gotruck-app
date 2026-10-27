package com.project.goTruck.service.company;

import com.project.goTruck.repository.CompanyFeatureRepository;
import com.project.goTruck.repository.CompanyRepository;
import com.project.goTruck.repository.UserFeatureRepository;
import com.project.goTruck.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyCompensationService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final CompanyFeatureRepository companyFeatureRepository;
    private final UserFeatureRepository userFeatureRepository;

    /**
     * Removes the local records created for a failed company workflow.
     * This method is called through this separate Spring bean, so @Transactional
     * is applied by Spring's proxy rather than bypassed by self-invocation.
     */
    @Transactional
    public void compensate(Long companyId, UUID keycloakId) {
        log.warn("Compensating: deleting records for keycloakId={}, companyId={}",
                keycloakId, companyId);

        userRepository.findByKeycloakId(keycloakId).ifPresent(user -> {
            userFeatureRepository.deleteByUserId(user.getId());
            userRepository.delete(user);
        });

        companyFeatureRepository.deleteByCompanyId(companyId);
        companyRepository.findById(companyId).ifPresent(companyRepository::delete);
    }
}
