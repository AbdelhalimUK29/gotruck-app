package com.project.goTruck.repository;

import com.project.goTruck.entity.UserRental;
import com.project.goTruck.entity.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRentalRepository extends JpaRepository<UserRental, Long> {

    Optional<UserRental> findByUserIdAndRentalId(Long userId, Long rentalId);

    List<UserRental> findByUserId(Long userId);

    List<UserRental> findByRentalId(Long rentalId);

    boolean existsByUserIdAndRentalId(Long userId, Long rentalId);

    void deleteByUserIdAndRentalId(Long userId, Long rentalId);

    void deleteByRentalIdAndUserIdIn(Long rentalId, List<Long> userIds);

    @Query("""
            SELECT ur FROM UserRental ur
            LEFT JOIN FETCH ur.rental r
            LEFT JOIN FETCH r.company
            WHERE ur.user.id = :userId
            """)
    List<UserRental> findByUserIdWithRentalAndCompany(@Param("userId") Long userId);

    @Query("""
            SELECT ur FROM UserRental ur
            LEFT JOIN FETCH ur.user u
            WHERE ur.rental.id = :rentalId
            """)
    List<UserRental> findByRentalIdWithUsers(@Param("rentalId") Long rentalId);

    @Query("""
            SELECT ur FROM UserRental ur
            WHERE ur.rental.id = :rentalId AND ur.user.id IN :userIds
            """)
    List<UserRental> findByRentalIdAndUserIdIn(@Param("rentalId") Long rentalId, @Param("userIds") List<Long> userIds);
}