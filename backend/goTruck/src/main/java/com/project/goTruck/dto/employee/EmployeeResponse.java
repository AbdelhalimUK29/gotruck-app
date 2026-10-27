package com.project.goTruck.dto.employee;

public record EmployeeResponse(

        Long id,

        String username,

        String email,

        String firstName,

        String lastName

) {
}