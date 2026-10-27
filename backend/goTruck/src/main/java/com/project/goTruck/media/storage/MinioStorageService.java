package com.project.goTruck.media.storage;

import com.project.goTruck.config.StorageProperties;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Thin, reusable object-storage operations for the media pipeline.
 *
 * Knows about the two configured buckets (private temp, public read)
 * but nothing about logos or companies — key construction and
 * lifecycle decisions live in the calling services.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioStorageService {

    private final MinioClient minioClient;
    private final StorageProperties properties;

    /**
     * Uploads bytes to the private temp bucket.
     */
    public void uploadTemp(String objectKey, byte[] data, String contentType) {
        upload(properties.getTempBucket(), objectKey, data, contentType);
    }

    /**
     * Uploads bytes to the public bucket (anonymous read access via
     * the bucket policy applied at startup).
     */
    public void uploadPublic(String objectKey, byte[] data, String contentType) {
        upload(properties.getPublicBucket(), objectKey, data, contentType);
    }

    /**
     * Downloads an object from the private temp bucket.
     */
    public byte[] downloadTemp(String objectKey) {
        return download(properties.getTempBucket(), objectKey);
    }

    /**
     * Deletes an object from the private temp bucket. Missing objects
     * are treated as success — cleanup must be idempotent.
     */
    public void deleteTemp(String objectKey) {
        delete(properties.getTempBucket(), objectKey);
    }

    /**
     * Deletes an object from the public bucket. Missing objects are
     * treated as success.
     */
    public void deletePublic(String objectKey) {
        delete(properties.getPublicBucket(), objectKey);
    }

    /**
     * Returns true when the temp object exists.
     */
    public boolean tempObjectExists(String objectKey) {
        return objectExists(properties.getTempBucket(), objectKey);
    }

    /**
     * Returns true when the public object exists.
     */
    public boolean publicObjectExists(String objectKey) {
        return objectExists(properties.getPublicBucket(), objectKey);
    }

    /**
     * Lists object keys in the temp bucket under the given prefix,
     * together with their last-modified timestamps — used by the
     * abandoned-temp cleanup job.
     */
    public List<TempObject> listTempObjects(String prefix) {
        List<TempObject> objects = new ArrayList<>();
        Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(properties.getTempBucket())
                        .prefix(prefix)
                        .recursive(true)
                        .build());

        for (Result<Item> result : results) {
            try {
                Item item = result.get();
                objects.add(new TempObject(
                        item.objectName(),
                        item.lastModified() == null
                                ? null
                                : item.lastModified().toInstant()));
            } catch (Exception e) {
                // A single unreadable listing entry must not abort the
                // whole cleanup scan.
                log.warn("Skipping unreadable temp object listing entry", e);
            }
        }
        return objects;
    }

    /**
     * Lists object keys in the public bucket under the given prefix,
     * together with their last-modified timestamps — used by the
     * orphaned-public-logo cleanup job.
     */
    public List<TempObject> listPublicObjects(String prefix) {
        List<TempObject> objects = new ArrayList<>();
        Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(properties.getPublicBucket())
                        .prefix(prefix)
                        .recursive(true)
                        .build());

        for (Result<Item> result : results) {
            try {
                Item item = result.get();
                objects.add(new TempObject(
                        item.objectName(),
                        item.lastModified() == null
                                ? null
                                : item.lastModified().toInstant()));
            } catch (Exception e) {
                // A single unreadable listing entry must not abort the
                // whole cleanup scan.
                log.warn("Skipping unreadable public object listing entry", e);
            }
        }
        return objects;
    }

    /**
     * A temp-bucket object with its key and last-modified timestamp.
     */
    public record TempObject(String key, Instant lastModified) {
    }

    private void upload(String bucket, String objectKey, byte[] data,
                        String contentType) {
        try (InputStream stream = new ByteArrayInputStream(data)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(stream, data.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new StorageException(
                    "Failed to upload object " + objectKey + " to bucket "
                            + bucket, e);
        }
    }

    private byte[] download(String bucket, String objectKey) {
        try (GetObjectResponse response = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .build())) {

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            response.transferTo(buffer);
            return buffer.toByteArray();
        } catch (Exception e) {
            throw new StorageException(
                    "Failed to download object " + objectKey
                            + " from bucket " + bucket, e);
        }
    }

    private void delete(String bucket, String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (ErrorResponseException e) {
            String code = e.errorResponse() == null
                    ? null
                    : e.errorResponse().code();
            if ("NoSuchKey".equals(code)) {
                // Already gone — deletion is idempotent.
                return;
            }
            throw new StorageException(
                    "Failed to delete object " + objectKey
                            + " from bucket " + bucket, e);
        } catch (Exception e) {
            throw new StorageException(
                    "Failed to delete object " + objectKey
                            + " from bucket " + bucket, e);
        }
    }

    private boolean objectExists(String bucket, String objectKey) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
            return true;
        } catch (ErrorResponseException e) {
            String code = e.errorResponse() == null
                    ? null
                    : e.errorResponse().code();
            if ("NoSuchKey".equals(code)) {
                return false;
            }
            throw new StorageException(
                    "Failed to stat object " + objectKey
                            + " in bucket " + bucket, e);
        } catch (Exception e) {
            throw new StorageException(
                    "Failed to stat object " + objectKey
                            + " in bucket " + bucket, e);
        }
    }
}
