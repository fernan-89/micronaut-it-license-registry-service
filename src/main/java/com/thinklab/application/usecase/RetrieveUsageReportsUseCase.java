package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.UsageReportResponse;
import com.thinklab.application.mapper.LicenseMapper;
import com.thinklab.domain.repository.UsageReportRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for listing the tenant's reports, newest first, optionally of one license, at most {@value #MAX_LIMIT} (BIAN Behavior Qualifier: {@code usage/retrieve}, collection). */
@Singleton
public class RetrieveUsageReportsUseCase {

    static final int DEFAULT_LIMIT = 50;
    static final int MAX_LIMIT = 200;

    private static final Logger log = LoggerFactory.getLogger(RetrieveUsageReportsUseCase.class);

    private final UsageReportRepository repository;

    public RetrieveUsageReportsUseCase(UsageReportRepository repository) {
        this.repository = repository;
    }

    public Flux<UsageReportResponse> execute(UUID organisationId, UUID licenseId, Integer limit, String role) {
        log.info("[USE CASE] Retrieving the UsageReports of organisation: {}", organisationId);

        return Mono.fromRunnable(() -> LicenseAccess.requireStaff(role, "list usage reports"))
                .then(Mono.fromCallable(() -> effectiveLimit(limit)))
                .flatMapMany(effective -> repository.findAll(organisationId, licenseId, effective))
                .map(LicenseMapper::toResponse);
    }

    private static int effectiveLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("The limit must be between 1 and " + MAX_LIMIT + ".");
        }
        return limit;
    }
}
