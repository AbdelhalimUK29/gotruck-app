package com.project.goTruck.controller.manager;

import com.project.goTruck.dto.rental.AssignUsersResult;
import com.project.goTruck.dto.rental.CreateRentalRequest;
import com.project.goTruck.dto.rental.RentalFilterDTO;
import com.project.goTruck.dto.rental.RentalResponse;
import com.project.goTruck.dto.rental.RentalUserResponse;
import com.project.goTruck.dto.rental.UpdateRentalRequest;
import com.project.goTruck.feature.FeatureRoleEnum;
import com.project.goTruck.security.argumentresolver.SessionCompanyId;
import com.project.goTruck.service.rental.RentalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/manager/rentals")
@RequiredArgsConstructor
@Slf4j
public class RentalController {

    private final RentalService rentalService;

    @PostMapping
    @PreAuthorize("hasPermission(null, T(com.project.goTruck.feature.FeatureRoleEnum).RENTAL)")
    public ResponseEntity<RentalResponse> createRental(
            @Valid @RequestBody CreateRentalRequest request,
            @SessionCompanyId Long companyId
    ) {
        RentalResponse response = rentalService.createRental(companyId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasPermission(null, T(com.project.goTruck.feature.FeatureRoleEnum).RENTAL)")
    public ResponseEntity<Page<RentalResponse>> getRentalsFilter(
            @Valid RentalFilterDTO filter,
            @SessionCompanyId Long companyId
    ) {
        Page<RentalResponse> page = rentalService.getFilteredRentals(filter, companyId);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(#id, T(com.project.goTruck.feature.FeatureRoleEnum).RENTAL)")
    public ResponseEntity<RentalResponse> getRental(
            @PathVariable Long id,
            @SessionCompanyId Long companyId
    ) {
        RentalResponse response = rentalService.getRentalById(id, companyId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(#id, T(com.project.goTruck.feature.FeatureRoleEnum).RENTAL)")
    public ResponseEntity<RentalResponse> updateRental(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRentalRequest request,
            @SessionCompanyId Long companyId
    ) {
        RentalResponse response = rentalService.updateRental(id, companyId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(#id, T(com.project.goTruck.feature.FeatureRoleEnum).RENTAL)")
    public ResponseEntity<Void> deleteRental(
            @PathVariable Long id,
            @SessionCompanyId Long companyId
    ) {
        rentalService.deleteRental(id, companyId);
        return ResponseEntity.noContent().build();
    }

    // User-Rental associations
    @PostMapping("/{rentalId}/users")
    @PreAuthorize("hasPermission(#rentalId, T(com.project.goTruck.feature.FeatureRoleEnum).RENTAL)")
    public ResponseEntity<AssignUsersResult> assignUsersToRental(
            @PathVariable Long rentalId,
            @RequestParam List<Long> userIds,
            @SessionCompanyId Long companyId
    ) {
        AssignUsersResult result = rentalService.assignUsersToRental(userIds, rentalId, companyId);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{rentalId}/users")
    @PreAuthorize("hasPermission(#rentalId, T(com.project.goTruck.feature.FeatureRoleEnum).RENTAL)")
    public ResponseEntity<Void> removeUsersFromRental(
            @PathVariable Long rentalId,
            @RequestParam List<Long> userIds,
            @SessionCompanyId Long companyId
    ) {
        rentalService.removeUsersFromRental(userIds, rentalId, companyId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{rentalId}/users")
    @PreAuthorize("hasPermission(#rentalId, T(com.project.goTruck.feature.FeatureRoleEnum).RENTAL)")
    public ResponseEntity<List<RentalUserResponse>> getRentalUsers(
            @PathVariable Long rentalId,
            @SessionCompanyId Long companyId
    ) {
        List<RentalUserResponse> users = rentalService.getRentalUsers(rentalId, companyId);
        return ResponseEntity.ok(users);
    }
}