package com.thinklab.application.usecase;

import com.thinklab.domain.model.License;
import com.thinklab.domain.model.License.LicenseAuditEntry;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for pausing and resuming a license (BIAN Behavior Qualifier: {@code control/*}). */
@Singleton
public class ControlLicenseUseCase {

    private static final Logger log = LoggerFactory.getLogger(ControlLicenseUseCase.class);

    /** What a person can ask for; each constant says how the aggregate performs it. */
    public enum Action {
        PAUSE {
            @Override LicenseAuditEntry apply(License license, String executor) { return license.pause(executor); }
        },
        RESUME {
            @Override LicenseAuditEntry apply(License license, String executor) { return license.resume(executor); }
        };

        abstract LicenseAuditEntry apply(License license, String executor);
    }

    private final LicenseWorkflow workflow;

    public ControlLicenseUseCase(LicenseWorkflow workflow) {
        this.workflow = workflow;
    }

    public Mono<Void> execute(UUID id, UUID organisationId, Action action, String executor, String role) {
        log.info("[USE CASE] {} on License ID: {}", action, id);

        return workflow.apply(id, organisationId, role, "pause or resume a licence", license -> action.apply(license, executor));
    }
}
