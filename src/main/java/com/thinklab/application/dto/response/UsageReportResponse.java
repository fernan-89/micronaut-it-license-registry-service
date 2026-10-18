package com.thinklab.application.dto.response;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

@Serdeable
public record UsageReportResponse(UUID id, UUID organisationId, UUID licenseId, int inUse, Instant measuredAt, String reportedBy, Instant reportedAt) {}
