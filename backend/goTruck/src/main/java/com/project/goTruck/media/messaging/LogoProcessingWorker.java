package com.project.goTruck.media.messaging;

import com.project.goTruck.exception.ImageProcessingException;
import com.project.goTruck.media.config.RabbitLogoConfig;
import com.project.goTruck.media.processing.ImageProcessingService;
import com.project.goTruck.media.storage.LogoUploadGuardStore;
import com.project.goTruck.media.storage.MinioStorageService;
import com.project.goTruck.service.company.CompanyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Consumes logo-processing events from RabbitMQ and performs the
 * asynchronous image processing pipeline.
 *
 * The worker is idempotent: the same event may be delivered more than
 * once, and the permanent object key is deterministic based on the
 * uploadId, so redelivery is safe.
 *
 * Phase 11 scope: process image, upload to public bucket, and update
 * the company's logo in the database.
 *
 * All processing failures leave the company's current logo unchanged.
 * Failures throw exceptions to trigger Spring AMQP retry/DLQ mechanism.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogoProcessingWorker {

    private final ImageProcessingService imageProcessingService;
    private final LogoUploadGuardStore logoUploadGuardStore;
    private final MinioStorageService minioStorageService;
    private final CompanyService companyService;

    @RabbitListener(queues = RabbitLogoConfig.LOGO_PROCESSING_QUEUE)
    public void processLogo(LogoProcessingRequested event) {
        UUID uploadId = event.uploadId();
        Long companyId = event.companyId();
        String tempObjectKey = event.tempObjectKey();

        log.info("Logo processing started: uploadId={}, companyId={}", uploadId, companyId);

        // 1. Stale-job guard: if a newer upload exists, this job is obsolete.
        Optional<UUID> latestUploadId = logoUploadGuardStore.findLatestUploadId(companyId);
        if (latestUploadId.isPresent() && !latestUploadId.get().equals(uploadId)) {
            log.info("Logo processing job is stale: uploadId={}, latestUploadId={}, companyId={}",
                    uploadId, latestUploadId.get(), companyId);
            // Clean up the temp object for this obsolete job.
            minioStorageService.deleteTemp(tempObjectKey);
            return;
        }

        // 2. Retrieve the temp object.
        byte[] data;
        try {
            data = minioStorageService.downloadTemp(tempObjectKey);
        } catch (RuntimeException e) {
            log.warn("Temp object missing for uploadId={}, companyId={}: {}", uploadId, companyId, e.getMessage());
            // Missing temp object -> throw to trigger retry/DLQ
            throw new IllegalStateException("Temp object not found: " + tempObjectKey, e);
        }

        // 3. Process the image.
        byte[] webpData;
        try {
            webpData = imageProcessingService.processToWebp(data);
        } catch (ImageProcessingException e) {
            log.warn("Image processing failed for uploadId={}, companyId={}: {}", uploadId, companyId, e.getMessage());
            // Processing failure -> leave the current logo untouched, throw for retry/DLQ
            throw e;
        }

        // 3. Upload the final image to the public bucket.
        String permanentObjectKey = "company/" + companyId + "/logo/" + uploadId + ".webp";
        try {
            minioStorageService.uploadPublic(permanentObjectKey, webpData, "image/webp");
        } catch (RuntimeException e) {
            log.warn("Failed to upload permanent logo for uploadId={}, companyId={}: {}", uploadId, companyId, e.getMessage());
            // Permanent upload failure -> throw for retry/DLQ
            throw e;
        }

        // 4. Update the company's logo in the database (transactional).
        String oldLogoKey;
        try {
            oldLogoKey = companyService.updateCompanyLogo(companyId, permanentObjectKey);
        } catch (RuntimeException e) {
            log.warn("Failed to update company logo for uploadId={}, companyId={}: {}", uploadId, companyId, e.getMessage());
            // DB update failure -> leave the newly uploaded permanent object; the cleanup job will handle it.
            return;
        }

        // 5. Delete the old logo (if any and different from the new one).
        if (oldLogoKey != null && !oldLogoKey.equals(permanentObjectKey)) {
            try {
                minioStorageService.deletePublic(oldLogoKey);
                log.info("Deleted old logo: uploadId={}, companyId={}, oldKey={}", uploadId, companyId, oldLogoKey);
            } catch (RuntimeException e) {
                // Old-logo deletion failure -> log, leave the new logo in place; cleanup job will handle it.
                log.warn("Failed to delete old logo for uploadId={}, companyId={}: {}", uploadId, companyId, e.getMessage());
            }
        }

        // 6. Delete the temp object.
        minioStorageService.deleteTemp(tempObjectKey);

        log.info("Logo processing completed: uploadId={}, companyId={}", uploadId, companyId);
    }
}