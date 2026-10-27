package com.project.goTruck.media.config;

import com.project.goTruck.config.StorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO client and bucket bootstrap.
 *
 * On startup this configuration ensures both media buckets exist:
 *  - the private temp bucket (original uploads awaiting processing)
 *  - the public bucket with an anonymous-read policy, so the final
 *    optimized images (e.g. company logos) are fetchable by the
 *    frontend via plain URLs without presigned requests.
 *
 * Bucket creation is idempotent: existing buckets are left untouched.
 * A failure to reach MinIO fails startup loudly — the logo pipeline
 * is useless without storage, and silent degradation would only move
 * the failure to the first upload request.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MinioConfig {

    private final StorageProperties properties;

    @Bean
    public MinioClient minioClient() {
        MinioClient client = MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();

        ensureBucket(client, properties.getTempBucket(), false);
        ensureBucket(client, properties.getPublicBucket(), true);

        return client;
    }

    private void ensureBucket(MinioClient client, String bucket, boolean publicRead) {
        try {
            boolean exists = client.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build());

            if (!exists) {
                client.makeBucket(
                        MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created MinIO bucket: {}", bucket);
            }

            if (publicRead) {
                applyPublicReadPolicy(client, bucket);
            }
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to initialize MinIO bucket: " + bucket, e);
        }
    }

    /**
     * Anonymous s3:GetObject for the whole bucket; writes still require
     * credentials. Re-applying the policy on every startup is safe.
     */
    private void applyPublicReadPolicy(MinioClient client, String bucket)
            throws Exception {
        String policy = """
                {
                  "Version": "2012-10-17",
                  "Statement": [
                    {
                      "Effect": "Allow",
                      "Principal": {"AWS": ["*"]},
                      "Action": ["s3:GetObject"],
                      "Resource": ["arn:aws:s3:::%s/*"]
                    }
                  ]
                }
                """.formatted(bucket);

        client.setBucketPolicy(SetBucketPolicyArgs.builder()
                .bucket(bucket)
                .config(policy)
                .build());
    }
}
