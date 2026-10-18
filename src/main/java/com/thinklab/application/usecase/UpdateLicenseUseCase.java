package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.UpdateLicenseRequest;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for changing a license (BIAN Behavior Qualifier: {@code update}). */
@Singleton
public class UpdateLicenseUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateLicenseUseCase.class);

    private final LicenseWorkflow workflow;

    public UpdateLicenseUseCase(LicenseWorkflow workflow) {
        this.workflow = workflow;
    }

    public Mono<Void> execute(UUID id, UUID organisationId, UpdateLicenseRequest request, String executor, String role) {
        log.info("[USE CASE] Updating License ID: {}", id);

        return workflow.apply(id, organisationId, role, "change a licence",
                license -> license.update(request.product(), request.unit(), request.entitled(), request.warnPercent(), request.reportEveryHours(), request.vendorId(),
                        request.agreementId(), executor));
    }
}
