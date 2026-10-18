package com.thinklab.domain.repository;

import com.thinklab.domain.model.License;
import com.thinklab.domain.model.License.LicenseAuditEntry;
import com.thinklab.domain.model.License.LicenseStatus;
import com.thinklab.domain.model.UsageReport;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Outbound Port for License persistence. {@link #save} is a guarded write on the status loaded (ADR-033); every lookup is tenant-scoped. */
public interface LicenseRepository {

    /** Inserts the license. A name already used in the organisation is {@link com.thinklab.domain.exception.DuplicateLicenseException}: the unique index is the atomic backstop. */
    Mono<License> create(License license);

    Mono<License> findById(UUID id, UUID organisationId);

    /** The tenant's licenses, oldest first. */
    Flux<License> findAll(UUID organisationId);

    Mono<Void> save(License license, LicenseStatus expectedStatus, LicenseAuditEntry auditEntry);

    /**
     * Folds a reported report into the latest one the license keeps (ADR-030). It only moves forward in time: a report older than the one kept, or
     * the same one reported twice, changes nothing.
     */
    Mono<Void> recordReport(UUID licenseId, UUID organisationId, UsageReport report);
}
