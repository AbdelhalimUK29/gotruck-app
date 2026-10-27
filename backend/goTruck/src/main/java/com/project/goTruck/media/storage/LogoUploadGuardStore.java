package com.project.goTruck.media.storage;

import com.project.goTruck.config.MediaLogoProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Redis-backed stale-job guard for logo processing.
 *
 * Redis serves exactly ONE purpose in the logo pipeline: preventing
 * an older processing job from overwriting a newer logo when a
 * company uploads twice in quick succession. It is NOT the source
 * of truth (PostgreSQL companies.logo is) and the worker never
 * needs it to resolve the temp object — the RabbitMQ event carries
 * the tempObjectKey.
 *
 * Single key, TTL'd, written by the upload path and read by the
 * worker before touching the company logo:
 *
 *   logo:upload:latest:{companyId} -> uploadId
 *
 * When the key is absent (expired) the worker treats the job as
 * current — an expired guard must not block processing, since the
 * event itself is self-contained.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogoUploadGuardStore {

    private static final String LATEST_KEY_PREFIX = "logo:upload:latest:";

    private final StringRedisTemplate redisTemplate;
    private final MediaLogoProperties properties;

    /**
     * Builds the safe, generated temp-bucket object key for an
     * upload. The original filename is never used.
     */
    public static String tempObjectKey(Long companyId, UUID uploadId) {
        return "temp/company/" + companyId + "/" + uploadId + "/original";
    }

    /**
     * Marks an upload as the company's latest, with the configured
     * TTL. Any earlier upload for the same company becomes stale.
     */
    public void markLatest(UUID uploadId, Long companyId) {
        redisTemplate.opsForValue().set(
                LATEST_KEY_PREFIX + companyId,
                uploadId.toString(),
                properties.getRedisTtl());
    }

    /**
     * Returns the uploadId of the company's most recent upload, or
     * empty when the guard key expired. The worker compares it with
     * the event's uploadId: a mismatch means the job is stale and
     * must not touch the company logo; empty means the guard can no
     * longer say anything, so the job is treated as current.
     */
    public Optional<UUID> findLatestUploadId(Long companyId) {
        String value = redisTemplate.opsForValue()
                .get(LATEST_KEY_PREFIX + companyId);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid latest-upload guard value for company {}: "
                    + "discarding", companyId);
            return Optional.empty();
        }
    }
}
