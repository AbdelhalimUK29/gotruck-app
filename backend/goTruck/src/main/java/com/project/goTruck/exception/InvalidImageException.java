package com.project.goTruck.exception;

/**
 * Thrown when an uploaded company-logo file fails content-based
 * validation (empty, too large, unknown format, disallowed format,
 * excessive dimensions, or corrupted/undecodable data).
 *
 * Carries a client-safe message — it is rendered verbatim into the
 * 400 ProblemDetail response.
 */
public class InvalidImageException extends RuntimeException {

    public InvalidImageException(String message) {
        super(message);
    }
}
