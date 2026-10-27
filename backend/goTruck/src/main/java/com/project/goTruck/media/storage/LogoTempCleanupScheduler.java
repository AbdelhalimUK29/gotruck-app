package com.project.goTruck.media.storage;

import com.project.goTruck.config.MediaLogoProperties;
import com.project.goTruck.config.StorageProperties;
import com.project.goTruck.entity.Company;
import com.project.goTruck.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Scheduled cleanup job for abandoned temporary objects and orphaned
 * permanent logos.
 *
 * Runs periodically to clean up:
 *  1. Temp objects older than the configured retention threshold
 *     (default 24h, significantly longer than the Redis TTL so active
 *     uploads are never touched).
 *  2. Orphaned permanent objects in the public bucket that are not
 *     referenced by any company's logo field and are older than the
 *     retention threshold.
 *
 * Can be disabled with app.media.logo.cleanup.enabled=false.
 *
 * Uses SchedulingConfigurer with Duration-based fixed delay (like
 * CompanyProvisioningScheduler) for robust duration parsing that
 * accepts ISO-8601 durations (PT1H, PT30M) and common formats (1h, 30m).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.media.logo.cleanup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class LogoTempCleanupScheduler implements SchedulingConfigurer {

    private final MinioStorageService minioStorageService;
    private final LogoUploadGuardStore logoUploadGuardStore;
    private final CompanyRepository companyRepository;
    private final MediaLogoProperties mediaLogoProperties;
    private final StorageProperties storageProperties;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addFixedDelayTask(new FixedDelayTask(
                this::cleanupAbandonedTempObjects,
                mediaLogoProperties.getCleanupInterval(),
                mediaLogoProperties.getCleanupInterval()
        ));
        taskRegistrar.addFixedDelayTask(new FixedDelayTask(
                this::cleanupOrphanedPublicLogos,
                mediaLogoProperties.getCleanupInterval(),
                mediaLogoProperties.getCleanupInterval()
        ));
    }

    /**
     * Cleans up abandoned temporary objects.
     *
     * Runs on the configured interval; the retention threshold (default 24h) is
     * significantly larger than the Redis TTL (default 30m) so active
     * uploads are never touched.
     */
    public void cleanupAbandonedTempObjects() {
        try {
            log.info("Starting abandoned temp object cleanup");
            int deleted = 0;

            List<MinioStorageService.TempObject> tempObjects = minioStorageService.listTempObjects(
                    storageProperties.getTempBucket() + "/");

            Instant threshold = Instant.now().minus(mediaLogoProperties.getTempRetention());

            for (MinioStorageService.TempObject obj : tempObjects) {
                if (obj.lastModified() != null && obj.lastModified().isBefore(threshold)) {
                    try {
                        minioStorageService.deleteTemp(obj.key());
                        log.debug("Deleted abandoned temp object: {}", obj.key());
                    } catch (RuntimeException e) {
                        log.warn("Failed to delete temp object {}: {}", obj.key(), e.getMessage());
                    }
                }
            }

            log.info("Abandoned temp object cleanup completed");
        } catch (RuntimeException e) {
            log.error("Abandoned temp object cleanup failed", e);
        }
    }

    /**
     * Cleans up orphaned permanent logos.
     *
     * Scans the public bucket for objects under the company logo prefix
     * that are not referenced by any company's logo field and are older
     * than the retention threshold.
     */
    public void cleanupOrphanedPublicLogos() {
        try {
            log.info("Starting orphaned public logo cleanup");
            int deleted = 0;

            // Collect all currently referenced logo keys
            List<Company> companies = companyRepository.findAll();
            Set<String> referencedKeys = companyRepository.findAll().stream()
                    .map(Company::getLogo)
                    .filter(key -> key != null && !key.isBlank())
                    .collect(java.util.stream.Collectors.toSet());

            List<MinioStorageService.TempObject> publicObjects = minioStorageService.listPublicObjects(
                    storageProperties.getPublicBucket() + "/company/");

            Instant threshold = Instant.now().minus(mediaLogoProperties.getTempRetention());

            for (MinioStorageService.TempObject obj : publicObjects) {
                if (obj.lastModified() != null && obj.lastModified().isBefore(threshold)) {
                    if (!referencedKeys.contains(obj.key())) {
                        try {
                            minioStorageService.deletePublic(obj.key());
                            log.debug("Deleted orphaned public logo: {}", obj.key());
                        } catch (RuntimeException e) {
                            log.warn("Failed to delete orphaned public logo {}: {}", obj.key(), e.getMessage());
                        }
                    }
                }
            }

            log.info("Orphaned public logo cleanup completed");
        } catch (RuntimeException e) {
            log.error("Orphaned public logo cleanup failed", e);
        }
    }
}