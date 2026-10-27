package com.project.goTruck.media.storage;

/**
 * Thrown when an object-storage (MinIO) operation fails after retries
 * are exhausted or the failure is not transient.
 */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
