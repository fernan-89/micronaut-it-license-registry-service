package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.ReportUsageRequest;
import com.thinklab.application.dto.response.UsageReportResponse;
import com.thinklab.application.mapper.LicenseMapper;
import com.thinklab.domain.exception.LicenseNotFoundException;
import com.thinklab.domain.model.UsageReport;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.LicenseRepository;
import com.thinklab.domain.repository.UsageReportRepository;
import com.thinklab.domain.repository.UsageReportRepository.Stored;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Use Case for what the tool reports when it measures (BIAN Behavior Qualifier: {@code usage/initiate}), ADR-030. Idempotent: the same license
 * and moment is the same report, so a tool may retry; the stored report comes back with {@code created} false. Either way the license's latest
 * report is folded again (it only moves forward in time), so a report that stored the report and died before that update is repaired by the
 * retry. A sovereign id is spent only for a report that is new.
 */
@Singleton
public class ReportUsageUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReportUsageUseCase.class);

    /** The report as stored, and whether this report created it (201) or found it already there (200). */
    public record Reported(UsageReportResponse report, boolean created) {
    }

    private final HashServicePort hashServicePort;
    private final LicenseRepository licenses;
    private final UsageReportRepository reports;
    private final Clock clock;

    public ReportUsageUseCase(HashServicePort hashServicePort, LicenseRepository licenses, UsageReportRepository reports, Clock clock) {
        this.hashServicePort = hashServicePort;
        this.licenses = licenses;
        this.reports = reports;
        this.clock = clock;
    }

    public Mono<Reported> execute(UUID organisationId, ReportUsageRequest request, String executor, String role) {
        log.info("[USE CASE] Reporting a report for License ID: {}", request.licenseId());

        return Mono.fromRunnable(() -> LicenseAccess.requireStaff(role, "report seats in use"))
                .then(Mono.defer(() -> licenses.findById(request.licenseId(), organisationId)))
                .switchIfEmpty(Mono.error(new LicenseNotFoundException("License " + request.licenseId() + " not found.")))
                .flatMap(license -> reports.findByNaturalKey(organisationId, license.getId(), UsageReport.truncate(request.measuredAt()))
                        .map(existing -> new Stored(existing, false))
                        .switchIfEmpty(Mono.defer(() -> store(organisationId, request, executor))))
                .flatMap(stored -> licenses.recordReport(request.licenseId(), organisationId, stored.report())
                        .thenReturn(new Reported(LicenseMapper.toResponse(stored.report()), stored.created())));
    }

    private Mono<Stored> store(UUID organisationId, ReportUsageRequest request, String executor) {
        return hashServicePort.generateSovereignId("usage-report-creation")
                .map(id -> UsageReport.createNew(id, organisationId, request.licenseId(), request.inUse(), request.measuredAt(), executor, Instant.now(clock)))
                .flatMap(reports::create);
    }
}
