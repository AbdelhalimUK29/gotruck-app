package com.project.goTruck.dto.company;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateCompanyRequest(

        @NotNull(message = "Company ID is required")
        Long id,

        @Size(max = 255, message = "Company name must not exceed 255 characters")
        String name,

        @Size(max = 255, message = "Website must not exceed 255 characters")
        String website,

        @Email(message = "Email must be valid")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        String email,

        @Size(max = 255, message = "Location must not exceed 255 characters")
        String location,

        @Size(max = 100, message = "City must not exceed 100 characters")
        String city,

        @Size(max = 100, message = "Country must not exceed 100 characters")
        String country

) {
}