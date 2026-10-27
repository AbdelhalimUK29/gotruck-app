package com.project.goTruck.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.List;

/**
 * Tuning knobs for the company-logo upload and processing pipeline.
 *
 * All values can be overridden through relaxed binding, e.g.
 * APP_MEDIA_LOGO_MAX_UPLOAD_SIZE, APP_MEDIA_LOGO_MAX_WIDTH,
 * APP_MEDIA_LOGO_QUALITY or APP_MEDIA_LOGO_REDIS_TTL.
 *
 * Redis is only used for temporary upload state (status polling and
 * the stale-job guard); PostgreSQL companies.logo remains the single
 * source of truth for the current logo object key.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.media.logo")
public class MediaLogoProperties {

    /**
     * Maximum accepted upload size for a logo, enforced by the
     * application on top of the HTTP multipart limits. Rejected
     * uploads never reach MinIO.
     */
    private DataSize maxUploadSize = DataSize.ofMegabytes(10);

    /**
     * Maximum image width in pixels. Larger images are downscaled by
     * the worker (aspect ratio preserved, never upscaled).
     */
    private int maxWidth = 1024;

    /**
     * Maximum image height in pixels.
     */
    private int maxHeight = 1024;

    /**
     * WebP encode quality of the final image, 0.0 - 1.0.
     */
    private float quality = 0.85f;

    /**
     * TTL of the Redis upload-state records (status + latest-upload
     * guard). Should comfortably exceed the expected end-to-end
     * processing time; expired entries simply disappear.
     */
    private Duration redisTtl = Duration.ofMinutes(30);

    /**
     * Age threshold before an abandoned temporary MinIO object is
     * deleted by the cleanup job. Must be significantly larger than
     * redisTtl so active uploads are never touched.
     */
    private Duration tempRetention = Duration.ofHours(24);

    /**
     * Image formats accepted at upload time, detected from the actual
     * content — never from the filename, extension or client MIME type.
     */
    private List<String> allowedFormats = List.of("JPEG", "PNG", "WEBP");

    /**
     * Interval between cleanup runs for abandoned temp objects and
     * orphaned permanent logos. Uses Duration for flexible parsing
     * (e.g. 1h, PT1H, 30m, PT30M).
     */
    private Duration cleanupInterval = Duration.ofHours(1);
}
