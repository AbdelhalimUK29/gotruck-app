package com.project.goTruck.repository;

import com.project.goTruck.entity.Rental;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RentalRepository
        extends JpaRepository<Rental, Long>, JpaSpecificationExecutor<Rental> {

    List<Rental> findByCompanyIdAndDeletedFalse(Long companyId);

    Optional<Rental> findByIdAndCompanyIdAndDeletedFalse(Long id, Long companyId);

    @Query("""
            SELECT r FROM Rental r
            LEFT JOIN FETCH r.company
            WHERE r.id = :id AND r.company.id = :companyId AND r.deleted = false
            """)
    Optional<Rental> findByIdAndCompanyIdWithCompany(@Param("id") Long id, @Param("companyId") Long companyId);

    @Query("""
            SELECT r FROM Rental r
            LEFT JOIN FETCH r.userRentals ur
            LEFT JOIN FETCH ur.user
            WHERE r.id = :id AND r.company.id = :companyId AND r.deleted = false
            """)
    Optional<Rental> findByIdAndCompanyIdWithUsers(@Param("id") Long id, @Param("companyId") Long companyId);
}