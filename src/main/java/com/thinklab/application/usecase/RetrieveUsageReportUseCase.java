package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.UsageReportResponse;
import com.thinklab.application.mapper.LicenseMapper;
import com.thinklab.domain.exception.UsageReportNotFoundException;
import com.thinklab.domain.repository.UsageReportRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for reading one report of the tenant (BIAN Behavior Qualifier: {@code usage/retrieve}). Staff only; another tenant is a 404. */
@Singleton
public class RetrieveUsageReportUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveUsageReportUseCase.class);

    private final UsageReportRepository repository;

    public RetrieveUsageReportUseCase(UsageReportRepository repository) {
        this.repository = repository;
    }

    public Mono<UsageReportResponse> execute(UUID id, UUID organisationId, String role) {
        log.info("[USE CASE] Retrieving UsageReport ID: {}", id);

        return Mono.fromRunnable(() -> LicenseAccess.requireStaff(role, "read a usage report"))
                .then(Mono.defer(() -> repository.findById(id, organisationId)))
                .switchIfEmpty(Mono.error(new UsageReportNotFoundException("UsageReport " + id + " not found.")))
                .map(LicenseMapper::toResponse);
    }
}
