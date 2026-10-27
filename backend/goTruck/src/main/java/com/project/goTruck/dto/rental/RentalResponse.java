package com.project.goTruck.dto.rental;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentalResponse {

    private Long id;
    private Long companyId;
    private String name;
    private String city;
    private String location;
    private String email;
    private String logo;
    private Boolean deleted;
    private Instant createdAt;
    private Instant updatedAt;
    private List<RentalUserResponse> users;
}