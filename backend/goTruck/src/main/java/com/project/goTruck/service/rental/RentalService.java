package com.project.goTruck.service.rental;

import com.project.goTruck.dto.rental.AssignUsersResult;
import com.project.goTruck.dto.rental.CreateRentalRequest;
import com.project.goTruck.dto.rental.RentalFilterDTO;
import com.project.goTruck.dto.rental.RentalResponse;
import com.project.goTruck.dto.rental.UpdateRentalRequest;
import com.project.goTruck.entity.Company;
import com.project.goTruck.entity.Rental;
import com.project.goTruck.entity.UserRental;
import com.project.goTruck.entity.user.User;
import com.project.goTruck.exception.RentalNotFoundException;
import com.project.goTruck.exception.UserNotAuthorizedException;
import com.project.goTruck.exception.UserNotFoundException;
import com.project.goTruck.mapper.rental.RentalMapper;
import com.project.goTruck.repository.RentalRepository;
import com.project.goTruck.repository.UserRepository;
import com.project.goTruck.repository.UserRentalRepository;
import com.project.goTruck.security.current.CurrentUserPrincipal;
import com.project.goTruck.security.current.CurrentUserService;
import com.project.goTruck.specification.RentalSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RentalService {

    private final RentalRepository rentalRepository;
    private final UserRentalRepository userRentalRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final RentalMapper rentalMapper;

    @Transactional
    public RentalResponse createRental(Long companyId, CreateRentalRequest request) {
        com.project.goTruck.security.current.CurrentUserPrincipal currentUser = currentUserService.currentUser();
        User user = userRepository.findByKeycloakId(currentUser.keycloakId())
                .orElseThrow(() -> new com.project.goTruck.exception.UserNotFoundException("User not found"));

        Rental rental = rentalMapper.toEntity(request);
        rental.setCompany(Company.builder().id(companyId).build());
        rental = rentalRepository.save(rental);

        // Automatically associate the current user with the newly created rental
        UserRental userRental = UserRental.builder()
                .user(user)
                .rental(rental)
                .build();
        userRentalRepository.save(userRental);

        return rentalMapper.toResponse(rental);
    }

    @Transactional(readOnly = true)
    public Page<RentalResponse> getFilteredRentals(RentalFilterDTO filter, Long companyId) {
        filter.setCompanyId(companyId);

        // Get current user to check role
        CurrentUserPrincipal currentUser = currentUserService.currentUser();
        UUID currentUserKeycloakId = currentUser.keycloakId();

        User user = userRepository.findByKeycloakId(currentUserKeycloakId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        boolean isOwnerOrSuperAdmin = user.isOwner() || user.isSuperAdmin();
        Long userId = isOwnerOrSuperAdmin ? null : user.getId();

        Pageable pageable = filter.toPageable();
        Page<Rental> rentalPage = rentalRepository.findAll(RentalSpecification.build(filter, companyId, userId), pageable);
        return rentalPage.map(rentalMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public RentalResponse getRentalById(Long id, Long companyId) {
        Rental rental = rentalRepository.findByIdAndCompanyIdWithUsers(id, companyId)
                .orElseThrow(() -> new RentalNotFoundException(id));
        return rentalMapper.toResponseWithUsers(rental);
    }

    @Transactional
    public RentalResponse updateRental(Long id, Long companyId, UpdateRentalRequest request) {
        Rental rental = rentalRepository.findByIdAndCompanyIdAndDeletedFalse(id, companyId)
                .orElseThrow(() -> new RentalNotFoundException(id));

        rentalMapper.updateEntity(rental, request);
        rental = rentalRepository.save(rental);
        return rentalMapper.toResponse(rental);
    }

    @Transactional
    public void deleteRental(Long id, Long companyId) {
        Rental rental = rentalRepository.findByIdAndCompanyIdAndDeletedFalse(id, companyId)
                .orElseThrow(() -> new RentalNotFoundException(id));
        rental.setDeleted(true);
        rentalRepository.save(rental);
    }

    // User-Rental associations
    @Transactional
    public AssignUsersResult assignUsersToRental(List<Long> userIds, Long rentalId, Long companyId) {
        if (userIds == null || userIds.isEmpty()) {
            return AssignUsersResult.builder()
                    .assigned(List.of())
                    .alreadyAssigned(List.of())
                    .invalidUsers(List.of())
                    .build();
        }

        // 1. Validate rental exists and belongs to company
        Rental rental = rentalRepository.findByIdAndCompanyIdAndDeletedFalse(rentalId, companyId)
                .orElseThrow(() -> new RentalNotFoundException(rentalId));

        // 2. Bulk validate all users belong to the company
        List<User> validUsers = userRepository.findAllByIdInAndCompanyId(userIds, companyId);
        Set<Long> validUserIds = validUsers.stream().map(User::getId).collect(Collectors.toSet());
        List<Long> invalidUsers = userIds.stream()
                .filter(id -> !validUserIds.contains(id))
                .collect(Collectors.toList());

        // 3. Check existing associations for valid users
        List<UserRental> existingAssociations = userRentalRepository.findByRentalIdAndUserIdIn(rentalId, new ArrayList<>(validUserIds));
        Set<Long> alreadyAssignedUserIds = existingAssociations.stream()
                .map(ur -> ur.getUser().getId())
                .collect(Collectors.toSet());

        // 4. Determine users to newly assign (valid but not already assigned)
        List<Long> usersToAssign = validUserIds.stream()
                .filter(id -> !alreadyAssignedUserIds.contains(id))
                .collect(Collectors.toList());

        // 5. Bulk create new associations
        if (!usersToAssign.isEmpty()) {
            List<UserRental> newAssociations = usersToAssign.stream()
                    .map(userId -> UserRental.builder()
                            .user(User.builder().id(userId).build())
                            .rental(Rental.builder().id(rentalId).build())
                            .build())
                    .collect(Collectors.toList());
            userRentalRepository.saveAll(newAssociations);
        }

        return AssignUsersResult.builder()
                .assigned(usersToAssign)
                .alreadyAssigned(new ArrayList<>(alreadyAssignedUserIds))
                .invalidUsers(invalidUsers)
                .build();
    }

    @Transactional
    public void removeUsersFromRental(List<Long> userIds, Long rentalId, Long companyId) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }

        // Validate rental exists and belongs to company
        rentalRepository.findByIdAndCompanyIdAndDeletedFalse(rentalId, companyId)
                .orElseThrow(() -> new RentalNotFoundException(rentalId));

        // Bulk remove associations for all specified users
        userRentalRepository.deleteByRentalIdAndUserIdIn(rentalId, userIds);
    }

    @Transactional(readOnly = true)
    public List<com.project.goTruck.dto.rental.RentalUserResponse> getRentalUsers(Long rentalId, Long companyId) {
        rentalRepository.findByIdAndCompanyIdAndDeletedFalse(rentalId, companyId)
                .orElseThrow(() -> new RentalNotFoundException(rentalId)); // validate access
        return userRentalRepository.findByRentalIdWithUsers(rentalId).stream()
                .map(ur -> com.project.goTruck.dto.rental.RentalUserResponse.builder()
                        .id(ur.getUser().getId())
                        .username(ur.getUser().getUsername())
                        .email(ur.getUser().getEmail())
                        .firstName(ur.getUser().getFirstName())
                        .lastName(ur.getUser().getLastName())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RentalResponse> getUserRentals(Long userId) {
        return userRentalRepository.findByUserIdWithRentalAndCompany(userId).stream()
                .map(ur -> rentalMapper.toResponse(ur.getRental()))
                .collect(Collectors.toList());
    }
}