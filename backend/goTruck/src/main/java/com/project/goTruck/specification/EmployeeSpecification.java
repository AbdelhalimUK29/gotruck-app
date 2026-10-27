package com.project.goTruck.specification;

import com.project.goTruck.dto.employee.EmployeeFilterDTO;
import com.project.goTruck.entity.user.User;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EmployeeSpecification {

    private EmployeeSpecification() {}

    /**
     * Builds a combined Specification for employee filtering.
     *
     * @param filter        Filter criteria from request
     * @param companyId     Company ID from session (excludes other companies)
     * @param currentUserId Current user's Keycloak ID (excludes self)
     * @return Combined Specification applying all filters
     */
    public static Specification<User> build(EmployeeFilterDTO filter, Long companyId, UUID currentUserId) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Company filtering - only employees from the current company
            predicates.add(criteriaBuilder.equal(root.get("company").get("id"), companyId));

            // 2. Exclude owners (owner = false) — skipped when excludeUsers is true
            if (!filter.isExcludeUsers()) {
                predicates.add(criteriaBuilder.equal(root.get("owner"), false));
            }

            // 4. Exclude current authenticated user — skipped when excludeUsers is true
            if (currentUserId != null && !filter.isExcludeUsers()) {
                predicates.add(criteriaBuilder.notEqual(root.get("keycloakId"), currentUserId));
            }

            // 5. Fullname search (case-insensitive partial match on fullName column)
            if (filter.getFullname() != null && !filter.getFullname().isBlank()) {
                String searchTerm = "%" + filter.getFullname().toLowerCase() + "%";
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("fullName")), searchTerm));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}