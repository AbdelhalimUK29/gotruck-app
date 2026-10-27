package com.project.goTruck.exception;

public class KeycloakUserCreationException extends RuntimeException {

    public KeycloakUserCreationException(String message, Throwable cause) {
        super(message, cause);
    }

    public KeycloakUserCreationException(String message) {
        super(message);
    }
}