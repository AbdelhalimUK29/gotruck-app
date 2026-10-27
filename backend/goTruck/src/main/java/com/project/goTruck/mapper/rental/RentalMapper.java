package com.project.goTruck.mapper.rental;

import com.project.goTruck.dto.rental.CreateRentalRequest;
import com.project.goTruck.dto.rental.RentalResponse;
import com.project.goTruck.dto.rental.RentalUserResponse;
import com.project.goTruck.dto.rental.UpdateRentalRequest;
import com.project.goTruck.entity.Rental;
import com.project.goTruck.entity.UserRental;
import com.project.goTruck.entity.user.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", unmappedTargetPolicy = org.mapstruct.ReportingPolicy.ERROR)
public interface RentalMapper {

    RentalMapper INSTANCE = Mappers.getMapper(RentalMapper.class);

    /**
     * Maps CreateRentalRequest to Rental entity.
     * Fields company, id, deleted, createdAt, updatedAt, userRentals are set manually in service.
     */
    @Mapping(target = "company", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "userRentals", ignore = true)
    Rental toEntity(CreateRentalRequest request);

    /**
     * Updates Rental entity from UpdateRentalRequest.
     * Only non-null fields in request are updated.
     */
    @Mapping(target = "company", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "userRentals", ignore = true)
    void updateEntity(@MappingTarget Rental rental, UpdateRentalRequest request);

    /**
     * Maps Rental entity to RentalResponse.
     */
    @Mapping(target = "companyId", source = "company.id")
    @Mapping(target = "users", ignore = true)
    RentalResponse toResponse(Rental rental);

    /**
     * Maps a list of Rental entities to RentalResponse list.
     */
    default List<RentalResponse> toResponseList(List<Rental> rentals) {
        return rentals.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Maps Rental entity to RentalResponse with users.
     * Assumes userRentals and user are already loaded (via fetch join).
     */
    default RentalResponse toResponseWithUsers(Rental rental) {
        RentalResponse response = toResponse(rental);
        if (rental.getUserRentals() != null) {
            response.setUsers(rental.getUserRentals().stream()
                    .map(this::toUserResponse)
                    .collect(Collectors.toList()));
        }
        return response;
    }

    default RentalUserResponse toUserResponse(UserRental userRental) {
        if (userRental == null || userRental.getUser() == null) return null;
        User user = userRental.getUser();
        return RentalUserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .build();
    }
}