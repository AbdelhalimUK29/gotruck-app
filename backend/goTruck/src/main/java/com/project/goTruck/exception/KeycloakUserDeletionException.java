package com.project.goTruck.exception;

public class KeycloakUserDeletionException extends RuntimeException {

    public KeycloakUserDeletionException(String message, Throwable cause) {
        super(message, cause);
    }

    public KeycloakUserDeletionException(String message) {
        super(message);
    }
}