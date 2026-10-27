package com.project.goTruck.media.processing;

import com.project.goTruck.exception.InvalidImageException;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

/**
 * Content-based validation of uploaded images.
 *
 * A stateless utility, deliberately NOT a Spring bean: it has no
 * dependencies and one call site (the company-logo upload path in
 * CompanyService). Limits are passed in by the caller from
 * MediaLogoProperties so nothing is hardcoded here.
 *
 * Security model — the file CONTENT is the only trusted input:
 *  - the filename, extension and client-provided MIME type are
 *    completely ignored;
 *  - the format is detected by asking ImageIO which reader can
 *    actually decode the bytes;
 *  - dimensions are read from the header BEFORE any pixel decode,
 *    so decompression bombs are rejected cheaply;
 *  - a full decode proves the file is not truncated or corrupted.
 *
 * Validation order (cheapest first):
 *   empty -> size -> format detection -> allowed format ->
 *   header dimensions -> full decode
 */
@Slf4j
public final class ImageValidation {



    /**
     * Validates the raw upload bytes and returns the detected
     * content type (e.g. "image/png") suitable for use as the MinIO
     * object's content-type metadata.
     *
     * @param data           raw file bytes (never null at this point —
     *                       the caller checks the multipart part)
     * @param maxBytes       maximum accepted upload size in bytes
     * @param maxWidth       maximum image width in pixels
     * @param maxHeight      maximum image height in pixels
     * @param allowedFormats allowed ImageIO format names, uppercase
     *                       (e.g. JPEG, PNG, WEBP)
     * @return detected MIME content type
     * @throws InvalidImageException with a client-safe message when
     *                               any check fails
     */
    public static String validate(
            byte[] data,
            long maxBytes,
            int maxWidth,
            int maxHeight,
            Set<String> allowedFormats
    ) {
        if (data == null || data.length == 0) {
            throw new InvalidImageException("The uploaded file is empty.");
        }

        if (data.length > maxBytes) {
            throw new InvalidImageException(
                    "The uploaded file exceeds the maximum size of "
                            + maxBytes + " bytes.");
        }

        try (ImageInputStream stream = ImageIO.createImageInputStream(
                new ByteArrayInputStream(data))) {

            if (stream == null) {
                throw new InvalidImageException(
                        "The uploaded file is not a valid image.");
            }

            ImageReader reader = detectReader(stream);

            try {
                String formatName = readFormatName(reader);
                if (!allowedFormats.contains(formatName)) {
                    throw new InvalidImageException(
                            "Image format " + formatName + " is not allowed. "
                                    + "Allowed formats: " + allowedFormats + ".");
                }

                checkDimensions(reader, maxWidth, maxHeight);

                verifyDecodable(reader);

                return "image/" + formatName.toLowerCase(Locale.ROOT);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new InvalidImageException(
                    "The uploaded file could not be read as an image.");
        }
    }

    /**
     * Reads the format name reported by the detected reader,
     * normalized to uppercase (e.g. JPEG, PNG, WEBP).
     */
    private static String readFormatName(ImageReader reader) {
        try {
            return reader.getFormatName().toUpperCase(Locale.ROOT);
        } catch (IOException e) {
            throw new InvalidImageException(
                    "The uploaded file could not be read as an image.");
        }
    }

    /**
     * Finds the ImageIO reader that can decode the stream.
     * No reader means the content is not a recognizable image —
     * regardless of what the filename or MIME header claimed.
     *
     * The caller is responsible for keeping the stream alive and
     * disposing the reader after use.
     */
    private static ImageReader detectReader(ImageInputStream stream) {
        Iterator<ImageReader> readers =
                ImageIO.getImageReaders(stream);

        if (!readers.hasNext()) {
            throw new InvalidImageException(
                    "The uploaded file is not a valid image.");
        }

        ImageReader reader = readers.next();
        reader.setInput(stream);
        return reader;
    }

    /**
     * Reads width/height from the image header only — no pixel data
     * is decoded — and rejects images exceeding the configured
     * limits. This is the decompression-bomb guard.
     */
    private static void checkDimensions(
            ImageReader reader,
            int maxWidth,
            int maxHeight
    ) {
        try {
            int width = reader.getWidth(reader.getMinIndex());
            int height = reader.getHeight(reader.getMinIndex());

            if (width > maxWidth || height > maxHeight) {
                log.info("invalid image dimension");
                throw new InvalidImageException(
                        "Image dimensions " + width + "x" + height
                                + " exceed the maximum of "
                                + maxWidth + "x" + maxHeight + " pixels.");
            }
        } catch (InvalidImageException e) {
            throw e;
        } catch (IOException e) {
            throw new InvalidImageException(
                    "The uploaded file is not a valid image.");
        }
    }

    /**
     * Fully decodes the image to prove it is not truncated or
     * corrupted. A header can look fine while the pixel data is
     * garbage; the worker would fail later on such a file, so the
     * upload is rejected up front.
     */
    private static void verifyDecodable(ImageReader reader) {
        try {
            reader.read(reader.getMinIndex());
        } catch (IOException | RuntimeException e) {
            throw new InvalidImageException(
                    "The uploaded image is corrupted or truncated.");
        }
    }
}
