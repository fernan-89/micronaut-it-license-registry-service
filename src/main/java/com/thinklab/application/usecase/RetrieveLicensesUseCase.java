package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.LicenseResponse;
import com.thinklab.application.mapper.LicenseMapper;
import com.thinklab.domain.model.License;
import com.thinklab.domain.model.License.Assessment;
import com.thinklab.domain.model.License.Level;
import com.thinklab.domain.model.License.LicenseStatus;
import com.thinklab.domain.model.License.Unit;
import com.thinklab.domain.repository.LicenseRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Use Case for listing the tenant's licences, oldest first, judged now, filterable by status, unit, vendor and level (BIAN Behavior Qualifier:
 * {@code retrieve}, collection). The level is judged when read, so it is filtered in memory over the tenant's licences (ADR-031).
 */
@Singleton
public class RetrieveLicensesUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveLicensesUseCase.class);

    private final LicenseRepository repository;
    private final Clock clock;

    public RetrieveLicensesUseCase(LicenseRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Flux<LicenseResponse> execute(UUID organisationId, LicenseStatus status, Unit unit, UUID vendorId, Level level, String role) {
        log.info("[USE CASE] Retrieving the Licenses of organisation: {}", organisationId);
        return Mono.fromRunnable(() -> LicenseAccess.requireStaff(role, "list licences"))
                .thenMany(Flux.defer(() -> {
                    Instant now = Instant.now(clock);
                    return repository.findAll(organisationId)
                            .filter(license -> status == null || license.getStatus() == status)
                            .filter(license -> unit == null || license.getUnit() == unit)
                            .filter(license -> vendorId == null || vendorId.equals(license.getVendorId()))
                            .map(license -> new Judged(license, license.assess(now)))
                            .filter(judged -> level == null || judged.assessment().level() == level)
                            .map(judged -> LicenseMapper.toResponse(judged.license(), judged.assessment()));
                }));
    }

    private record Judged(License license, Assessment assessment) {}
}
