package com.project.goTruck.exception;

/**
 * Thrown when an error occurs during image processing (decoding, resizing, encoding).
 */
public class ImageProcessingException extends RuntimeException {

    public ImageProcessingException(String message) {
        super(message);
    }

    public ImageProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}