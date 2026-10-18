package com.thinklab.domain.repository;

import com.thinklab.domain.model.UsageReport;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/** Outbound Port for UsageReport persistence: reports are facts, inserted once and never changed. Every lookup is tenant-scoped. */
public interface UsageReportRepository {

    /**
     * Inserts the report. The same license and moment is the same report (a unique index): the loser of a repeated or simultaneous report gets the
     * stored one back and {@code created} false, so a tool may retry a report as often as it likes.
     */
    Mono<Stored> create(UsageReport report);

    /** The report already reported for that license and moment, if any. */
    Mono<UsageReport> findByNaturalKey(UUID organisationId, UUID licenseId, Instant measuredAt);

    Mono<UsageReport> findById(UUID id, UUID organisationId);

    /** The tenant's reports, newest first, at most {@code limit}, optionally of one license. */
    Flux<UsageReport> findAll(UUID organisationId, UUID licenseId, int limit);

    record Stored(UsageReport report, boolean created) {
    }
}
