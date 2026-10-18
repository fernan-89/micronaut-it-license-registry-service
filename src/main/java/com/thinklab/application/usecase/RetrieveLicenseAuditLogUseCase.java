package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.AuditEntryResponse;
import com.thinklab.application.mapper.LicenseMapper;
import com.thinklab.domain.exception.LicenseNotFoundException;
import com.thinklab.domain.repository.LicenseRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Use Case for the forensic ledger of a license (BIAN Behavior Qualifier: {@code audit-log/retrieve}). Staff only. */
@Singleton
public class RetrieveLicenseAuditLogUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveLicenseAuditLogUseCase.class);

    private final LicenseRepository repository;

    public RetrieveLicenseAuditLogUseCase(LicenseRepository repository) {
        this.repository = repository;
    }

    public Mono<List<AuditEntryResponse>> execute(UUID id, UUID organisationId, String role) {
        log.info("[USE CASE] Retrieving the audit log of License ID: {}", id);

        return Mono.fromRunnable(() -> LicenseAccess.requireStaff(role, "read the audit trail of a licence"))
                .then(Mono.defer(() -> repository.findById(id, organisationId)))
                .switchIfEmpty(Mono.error(new LicenseNotFoundException("License " + id + " not found.")))
                .map(license -> license.getAuditTrail().stream().map(LicenseMapper::toResponse).collect(Collectors.toList()));
    }
}
