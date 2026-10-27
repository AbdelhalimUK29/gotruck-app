package com.project.goTruck.repository;

import com.project.goTruck.entity.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository
        extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByKeycloakId(UUID keycloakId);

    Optional<User> findByIdAndCompanyId(Long id, Long companyId);

    @Query("""
            SELECT u FROM User u
            LEFT JOIN FETCH u.userFeatures uf
            LEFT JOIN FETCH uf.feature
            WHERE u.id = :id AND u.company.id = :companyId
            """)
    Optional<User> findByIdAndCompanyIdWithUserFeatures(@Param("id") Long id, @Param("companyId") Long companyId);

    @Query("""
            SELECT u FROM User u
            LEFT JOIN FETCH u.userFeatures uf
            LEFT JOIN FETCH uf.feature
            WHERE u.keycloakId = :keycloakId AND u.company.id = :companyId
            """)
    Optional<User> findByKeycloakIdAndCompanyIdWithUserFeatures(@Param("keycloakId") UUID keycloakId, @Param("companyId") Long companyId);

    @Query("""
            SELECT u FROM User u
            WHERE u.id IN :userIds AND u.company.id = :companyId
            """)
    List<User> findAllByIdInAndCompanyId(@Param("userIds") List<Long> userIds, @Param("companyId") Long companyId);

    boolean existsByKeycloakIdAndOwnerTrueOrSuperAdminTrue(UUID keycloakId);
}
