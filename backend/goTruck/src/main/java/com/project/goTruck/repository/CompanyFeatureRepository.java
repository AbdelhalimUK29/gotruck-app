package com.project.goTruck.repository;

import com.project.goTruck.entity.CompanyFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CompanyFeatureRepository
        extends JpaRepository<CompanyFeature, Long> {

    List<CompanyFeature> findByCompanyId(Long companyId);

    @Query("""
            SELECT cf FROM CompanyFeature cf
            JOIN FETCH cf.feature f
            WHERE cf.company.id = :companyId
            """)
    List<CompanyFeature> findByCompanyIdWithFeatures(@Param("companyId") Long companyId);

    @Modifying
    @Query("""
            DELETE FROM CompanyFeature cf
            WHERE cf.company.id = :companyId
            AND cf.feature.id = :featureId
            """)
    void deleteByCompanyIdAndFeatureId(
            @Param("companyId") Long companyId,
            @Param("featureId") Long featureId
    );

    @Modifying
    @Query("""
            DELETE FROM CompanyFeature cf
            WHERE cf.company.id = :companyId
            """)
    void deleteByCompanyId(@Param("companyId") Long companyId);
}