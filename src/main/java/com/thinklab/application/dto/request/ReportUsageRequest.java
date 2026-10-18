package com.thinklab.application.dto.request;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for {@code usage/initiate}: how many seats the inventory tool counted in use, and when. A count, never a list of users or devices. Reporting
 * the same licence and moment again answers the report already stored.
 */
@Serdeable
public record ReportUsageRequest(
        @NotNull(message = "The licence is required") UUID licenseId,
        @NotNull(message = "The seats in use are required") @PositiveOrZero(message = "The seats in use cannot be negative") Integer inUse,
        @NotNull(message = "When the seats were counted is required") Instant measuredAt
) {}
