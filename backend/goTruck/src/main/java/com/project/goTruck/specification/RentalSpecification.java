package com.project.goTruck.specification;

import com.project.goTruck.dto.rental.RentalFilterDTO;
import com.project.goTruck.entity.Rental;
import com.project.goTruck.entity.UserRental;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class RentalSpecification {

    private RentalSpecification() {}

    /**
     * Builds a combined Specification for rental filtering.
     *
     * @param filter    Filter criteria from request
     * @param companyId Company ID from session (filters by company)
     * @param userId    User ID to filter by (null = owner/superAdmin sees all, non-null = only user's rentals)
     * @return Combined Specification applying all filters
     */
    public static Specification<Rental> build(RentalFilterDTO filter, Long companyId, Long userId) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Company filtering - only rentals from the current company
            predicates.add(criteriaBuilder.equal(root.get("company").get("id"), companyId));

            // 2. Only non-deleted rentals (deleted = false)
            predicates.add(criteriaBuilder.equal(root.get("deleted"), false));

            // 3. Name search (case-insensitive partial match)
            if (filter.getName() != null && !filter.getName().isBlank()) {
                String searchTerm = "%" + filter.getName().toLowerCase() + "%";
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), searchTerm));
            }

            // 4. User-based filtering: if userId is provided, only show rentals assigned to that user
            if (userId != null) {
                Join<Rental, UserRental> userRentalsJoin =
                        root.join("userRentals", JoinType.INNER);
                predicates.add(criteriaBuilder.equal(userRentalsJoin.get("user").get("id"), userId));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}