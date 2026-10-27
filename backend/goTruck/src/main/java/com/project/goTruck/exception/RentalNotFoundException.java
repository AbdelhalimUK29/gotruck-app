package com.project.goTruck.exception;

public class RentalNotFoundException extends RuntimeException {
    public RentalNotFoundException(Long id) {
        super("Rental not found: id=" + id);
    }
}