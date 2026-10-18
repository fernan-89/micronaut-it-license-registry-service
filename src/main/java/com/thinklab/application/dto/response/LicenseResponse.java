package com.thinklab.application.dto.response;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

/** A licence and where it stands right now: {@code level}, {@code usagePercent}, {@code available} and {@code stale} are judged when read, never stored. */
@Serdeable
public record LicenseResponse(UUID id, UUID organisationId, String product, String unit, int entitled, int warnPercent, int reportEveryHours, @Nullable UUID vendorId,
                              @Nullable UUID agreementId, String status, String level, @Nullable Double usagePercent, @Nullable Integer available, boolean stale,
                              @Nullable Integer lastInUse, @Nullable Instant lastMeasuredAt, Instant createdAt, Instant updatedAt) {}
