package com.thinklab.application.usecase;

import com.thinklab.domain.exception.LicenseNotFoundException;
import com.thinklab.domain.model.License;
import com.thinklab.domain.model.License.LicenseAuditEntry;
import com.thinklab.domain.repository.LicenseRepository;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.function.Function;

/** The shape every staff change to a license shares: staff only, load (a 404 if it is not the tenant's), apply, save with the status loaded. */
@Singleton
public class LicenseWorkflow {

    private final LicenseRepository repository;

    public LicenseWorkflow(LicenseRepository repository) {
        this.repository = repository;
    }

    public Mono<Void> apply(UUID id, UUID organisationId, String role, String operation, Function<License, LicenseAuditEntry> action) {
        return Mono.fromRunnable(() -> LicenseAccess.requireStaff(role, operation))
                .then(Mono.defer(() -> repository.findById(id, organisationId)))
                .switchIfEmpty(Mono.error(new LicenseNotFoundException("License " + id + " not found.")))
                .flatMap(license -> {
                    var statusBefore = license.getStatus();
                    LicenseAuditEntry entry = action.apply(license);
                    return repository.save(license, statusBefore, entry);
                });
    }
}
