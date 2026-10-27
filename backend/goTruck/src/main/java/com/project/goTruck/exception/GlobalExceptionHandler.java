package com.project.goTruck.exception;

import com.project.goTruck.media.storage.StorageException;
import jakarta.annotation.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice
public class GlobalExceptionHandler {

    public static final String COMPANY_ALREADY_EXISTS = "company/already-exists";
    public static final String COMPANY_NOT_FOUND = "company/not-found";
    public static final String KEYCLOAK_UPDATE_FAILED = "company/keycloak-update-failed";
    public static final String KEYCLOAK_USER_CREATION_FAILED = "employee/keycloak-creation-failed";
    public static final String KEYCLOAK_USER_DELETION_FAILED = "employee/keycloak-deletion-failed";
    public static final String VALIDATION_ERROR = "validation/error";
    public static final String INVALID_IMAGE = "logo/invalid-image";
    public static final String STORAGE_UNAVAILABLE = "media/storage-unavailable";
    public static final String IMAGE_PROCESSING_ERROR = "media/processing-error";
    public static final String USER_NOT_FOUND = "user/not-found";
    public static final String FEATURE_NOT_ENABLED_FOR_COMPANY = "user/feature-not-enabled";
    public static final String INVALID_FEATURE = "user/invalid-feature";
    public static final String USER_NOT_AUTHORIZED = "user/not-authorized";
    public static final String RENTAL_NOT_FOUND = "rental/not-found";

    @ExceptionHandler(StorageException.class)
    public ProblemDetail handleStorage(StorageException ex) {

        return createProblemDetail(
                HttpStatus.BAD_GATEWAY,
                STORAGE_UNAVAILABLE,
                "The media storage service is currently unavailable."
        );
    }

    @ExceptionHandler(InvalidImageException.class)
    public ProblemDetail handleInvalidImage(InvalidImageException ex) {

        return createProblemDetail(
                HttpStatus.BAD_REQUEST,
                INVALID_IMAGE,
                ex.getMessage()
        );
    }

    @ExceptionHandler(ImageProcessingException.class)
    public ProblemDetail handleImageProcessing(ImageProcessingException ex) {

        return createProblemDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                IMAGE_PROCESSING_ERROR,
                "Image processing failed."
        );
    }

    @ExceptionHandler(CompanyAlreadyExistsException.class)
    public ProblemDetail handleCompanyAlreadyExists(CompanyAlreadyExistsException ex) {

        return createProblemDetail(
                HttpStatus.CONFLICT,
                COMPANY_ALREADY_EXISTS,
                ex.getMessage()
        );
    }

    @ExceptionHandler(CompanyNotFoundException.class)
    public ProblemDetail handleCompanyNotFound(CompanyNotFoundException ex) {

        return createProblemDetail(
                HttpStatus.NOT_FOUND,
                COMPANY_NOT_FOUND,
                ex.getMessage()
        );
    }

    @ExceptionHandler(KeycloakUpdateException.class)
    public ProblemDetail handleKeycloakUpdate(KeycloakUpdateException ex) {

        return createProblemDetail(
                HttpStatus.BAD_GATEWAY,
                KEYCLOAK_UPDATE_FAILED,
                ex.getMessage()
        );
    }

    @ExceptionHandler(KeycloakUserCreationException.class)
    public ProblemDetail handleKeycloakUserCreation(KeycloakUserCreationException ex) {

        return createProblemDetail(
                HttpStatus.BAD_GATEWAY,
                KEYCLOAK_USER_CREATION_FAILED,
                ex.getMessage()
        );
    }

    @ExceptionHandler(KeycloakUserDeletionException.class)
    public ProblemDetail handleKeycloakUserDeletion(KeycloakUserDeletionException ex) {

        return createProblemDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                KEYCLOAK_USER_DELETION_FAILED,
                ex.getMessage()
        );
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ProblemDetail handleUserNotFound(UserNotFoundException ex) {

        return createProblemDetail(
                HttpStatus.NOT_FOUND,
                USER_NOT_FOUND,
                ex.getMessage()
        );
    }

    @ExceptionHandler(FeatureNotEnabledForCompanyException.class)
    public ProblemDetail handleFeatureNotEnabledForCompany(FeatureNotEnabledForCompanyException ex) {

        return createProblemDetail(
                HttpStatus.BAD_REQUEST,
                FEATURE_NOT_ENABLED_FOR_COMPANY,
                ex.getMessage()
        );
    }

    @ExceptionHandler(InvalidFeatureException.class)
    public ProblemDetail handleInvalidFeature(InvalidFeatureException ex) {

        return createProblemDetail(
                HttpStatus.BAD_REQUEST,
                INVALID_FEATURE,
                ex.getMessage()
        );
    }

    @ExceptionHandler(UserNotAuthorizedException.class)
    public ProblemDetail handleUserNotAuthorized(UserNotAuthorizedException ex) {

        return createProblemDetail(
                HttpStatus.FORBIDDEN,
                USER_NOT_AUTHORIZED,
                ex.getMessage()
        );
    }

    @ExceptionHandler(RentalNotFoundException.class)
    public ProblemDetail handleRentalNotFound(RentalNotFoundException ex) {

        return createProblemDetail(
                HttpStatus.NOT_FOUND,
                RENTAL_NOT_FOUND,
                ex.getMessage()
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        // Check if caused by our custom deserializer
        Throwable cause = ex.getCause();
        if (cause instanceof InvalidFeatureException) {
            return handleInvalidFeature((InvalidFeatureException) cause);
        }
        // Generic JSON parse error
        return createProblemDetail(
                HttpStatus.BAD_REQUEST,
                VALIDATION_ERROR,
                "Malformed JSON request"
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {

        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");

        return createProblemDetail(
                HttpStatus.BAD_REQUEST,
                VALIDATION_ERROR,
                message
        );
    }

    private ProblemDetail createProblemDetail(
            HttpStatus status,
            String type,
            String detail
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(type));
        problem.setTitle(status.getReasonPhrase());
        return problem;
    }
}