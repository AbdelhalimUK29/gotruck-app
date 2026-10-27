package com.project.goTruck.exception;

import java.util.Set;

public class InvalidFeatureException extends RuntimeException {

    private final Set<String> invalidFeatures;

    public InvalidFeatureException(String message, Set<String> invalidFeatures) {
        super(message);
        this.invalidFeatures = invalidFeatures;
    }

    public Set<String> getInvalidFeatures() {
        return invalidFeatures;
    }
}