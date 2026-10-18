package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.LicenseResponse;
import com.thinklab.application.mapper.LicenseMapper;
import com.thinklab.domain.exception.LicenseNotFoundException;
import com.thinklab.domain.repository.LicenseRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Use Case for reading one licence of the tenant, judged now (BIAN Behavior Qualifier: {@code retrieve}). Staff only; another tenant is a 404. */
@Singleton
public class RetrieveLicenseUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveLicenseUseCase.class);

    private final LicenseRepository repository;
    private final Clock clock;

    public RetrieveLicenseUseCase(LicenseRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Mono<LicenseResponse> execute(UUID id, UUID organisationId, String role) {
        log.info("[USE CASE] Retrieving License ID: {}", id);
        return Mono.fromRunnable(() -> LicenseAccess.requireStaff(role, "read a licence"))
                .then(Mono.defer(() -> repository.findById(id, organisationId)))
                .switchIfEmpty(Mono.error(new LicenseNotFoundException("License " + id + " not found.")))
                .map(license -> LicenseMapper.toResponse(license, license.assess(Instant.now(clock))));
    }
}
