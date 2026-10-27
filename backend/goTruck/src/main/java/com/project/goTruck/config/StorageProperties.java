package com.project.goTruck.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MinIO object-storage connection and bucket configuration.
 *
 * All values can be overridden through relaxed binding, e.g.
 * APP_STORAGE_MINIO_ENDPOINT, APP_STORAGE_MINIO_ACCESS_KEY,
 * APP_STORAGE_MINIO_TEMP_BUCKET or APP_STORAGE_MINIO_PUBLIC_BUCKET.
 *
 * Two dedicated buckets are used:
 *  - temp-bucket: private, holds original uploads while they await
 *    asynchronous processing; objects are deleted by the worker or
 *    by the abandoned-temp cleanup job.
 *  - public-bucket: public-read policy, holds the final optimized
 *    images (e.g. company logos) that the frontend fetches directly.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.storage.minio")
public class StorageProperties {

    /**
     * MinIO (or S3-compatible) endpoint URL, e.g. http://minio:9000.
     */
    private String endpoint = "http://localhost:9000";

    /**
     * Access key (MinIO user).
     */
    private String accessKey = "gotruck";

    /**
     * Secret key (MinIO password).
     */
    private String secretKey = "minio123";

    /**
     * Private bucket for temporary original uploads awaiting processing.
     */
    private String tempBucket = "gotruck-media-temp";

    /**
     * Public-read bucket for final optimized images served to clients.
     */
    private String publicBucket = "gotruck-media-public";
}
