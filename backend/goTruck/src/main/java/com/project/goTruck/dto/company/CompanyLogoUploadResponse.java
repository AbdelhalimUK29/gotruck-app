package com.project.goTruck.dto.company;

import java.util.UUID;

/**
 * Response returned immediately after a company-logo upload has been
 * accepted for asynchronous processing (HTTP 202).
 *
 * The image is not processed yet — the worker picks it up from
 * RabbitMQ. The frontend simply shows a local "processing" state and
 * refreshes the company data once the logo is ready.
 *
 * @param uploadId unique identity of this upload
 */
public record CompanyLogoUploadResponse(
        UUID uploadId
) {
}
