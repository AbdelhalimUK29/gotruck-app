package com.project.goTruck.media.messaging;

import java.util.UUID;

/**
 * RabbitMQ event requesting asynchronous processing of an uploaded
 * company logo.
 *
 * The payload is deliberately SELF-CONTAINED: it carries the
 * tempObjectKey so the worker never has to read Redis to resolve the
 * temporary object. An expired Redis entry therefore cannot break
 * processing.
 *
 * The binary image is never placed in the message — only the small
 * coordinates needed to fetch it from MinIO.
 *
 * @param uploadId      unique identity of this upload; also the
 *                      deterministic component of the permanent
 *                      object key, making redelivery idempotent
 * @param companyId     owning company
 * @param tempObjectKey temp-bucket key of the original upload
 */
public record LogoProcessingRequested(
        UUID uploadId,
        Long companyId,
        String tempObjectKey
) {
}
