package com.project.goTruck.repository;

import com.project.goTruck.entity.UserFeature;
import com.project.goTruck.feature.FeatureRoleEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface UserFeatureRepository
        extends JpaRepository<UserFeature, Long> {

    List<UserFeature> findByUserId(Long userId);

    @Modifying
    @Query("""
            DELETE FROM UserFeature uf
            WHERE uf.feature.id = :featureId
            AND uf.user.id IN (
                SELECT u.id FROM User u WHERE u.company.id = :companyId
            )
            """)
    void deleteByCompanyIdAndFeatureId(
            @Param("companyId") Long companyId,
            @Param("featureId") Long featureId
    );

    @Modifying
    @Query("""
            DELETE FROM UserFeature uf
            WHERE uf.user.id = :userId
            """)
    void deleteByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("""
            DELETE FROM UserFeature uf
            WHERE uf.user.id = :userId
            AND uf.feature.name IN :featureNames
            """)
    void deleteByUserIdAndFeatureNames(
            @Param("userId") Long userId,
            @Param("featureNames") Set<FeatureRoleEnum> featureNames
    );

    boolean existsByUserIdAndFeatureName(Long userId, FeatureRoleEnum featureName);
}