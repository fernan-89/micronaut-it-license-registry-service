package com.thinklab.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Secondary aggregate of the IT License Registry Service Domain: how many seats of a licence were in use at a moment, as the inventory tool that
 * counted them reported it (ADR-030). It is a fact, so it is immutable: never edited, never deleted by hand (old ones expire, ADR-033). Reporting
 * the same licence and moment twice is the same report. A count, never a list: no user, device name or host is kept.
 */
public class UsageReport {

    /** How far ahead of the registry's clock a report may claim to have been taken: the tool's clock is allowed to be a little off. */
    public static final Duration CLOCK_SKEW = Duration.ofMinutes(5);

    private final UUID id;
    private final UUID organisationId;
    private final UUID licenseId;
    private final int inUse;
    private final Instant measuredAt;
    private final String reportedBy;
    private final Instant reportedAt;

    private UsageReport(UUID id, UUID organisationId, UUID licenseId, int inUse, Instant measuredAt, String reportedBy, Instant reportedAt) {
        this.id = id;
        this.organisationId = organisationId;
        this.licenseId = licenseId;
        this.inUse = inUse;
        this.measuredAt = measuredAt;
        this.reportedBy = reportedBy;
        this.reportedAt = reportedAt;
    }

    public static UsageReport createNew(UUID id, UUID organisationId, UUID licenseId, int inUse, Instant measuredAt, String executor, Instant now) {
        if (id == null || organisationId == null || licenseId == null) {
            throw new IllegalArgumentException("ID, Organisation ID and License ID are mandatory for a UsageReport.");
        }
        if (inUse < 0) {
            throw new IllegalArgumentException("The seats in use must be zero or more.");
        }
        if (measuredAt == null) {
            throw new IllegalArgumentException("When the report was taken is mandatory.");
        }
        Instant moment = truncate(measuredAt);
        if (moment.isAfter(now.plus(CLOCK_SKEW))) {
            throw new IllegalArgumentException("A report cannot be taken in the future.");
        }
        if (executor == null || executor.isBlank()) {
            throw new IllegalArgumentException("Executor is mandatory to report a UsageReport.");
        }
        return new UsageReport(id, organisationId, licenseId, inUse, moment, executor, now);
    }

    public static UsageReport reconstitute(UUID id, UUID organisationId, UUID licenseId, int inUse, Instant measuredAt, String reportedBy, Instant reportedAt) {
        if (id == null || organisationId == null || licenseId == null || measuredAt == null) {
            throw new IllegalArgumentException("ID, Organisation ID, License ID and the moment are mandatory to reconstitute a UsageReport.");
        }
        return new UsageReport(id, organisationId, licenseId, inUse, measuredAt, reportedBy, reportedAt);
    }

    /** The store keeps milliseconds, and the moment is part of the report's identity: every time is cut to the millisecond before it is compared or looked up. */
    public static Instant truncate(Instant moment) {
        return moment.truncatedTo(ChronoUnit.MILLIS);
    }

    public UUID getId() { return id; }
    public UUID getOrganisationId() { return organisationId; }
    public UUID getLicenseId() { return licenseId; }
    public int getInUse() { return inUse; }
    public Instant getMeasuredAt() { return measuredAt; }
    public String getReportedBy() { return reportedBy; }
    public Instant getReportedAt() { return reportedAt; }
}
