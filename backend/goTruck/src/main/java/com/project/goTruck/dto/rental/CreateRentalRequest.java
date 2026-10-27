package com.project.goTruck.dto.rental;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRentalRequest {

    private String name;

    @Size(max = 100)
    private String city;

    @Size(max = 255)
    private String location;

    @Email
    @Size(max = 255)
    private String email;

    @Size(max = 512)
    private String logo;
}