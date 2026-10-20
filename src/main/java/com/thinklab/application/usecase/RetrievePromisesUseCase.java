package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.PromiseResponse;
import com.thinklab.domain.model.License;
import com.thinklab.domain.model.Promise;
import com.thinklab.domain.repository.LicenseRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Use Case for the promises of the tenant's licences, judged now (BIAN Behavior Qualifier: {@code promises/retrieve}, platform ADR-001): a licence with more seats
 * in use than bought is broken ({@code LICENSE_OVER_LIMIT}), one that is OK or near the limit is kept, one paused or without a count is not judged. The name is
 * the product; a licence has no asset. Staff only.
 */
@Singleton
public class RetrievePromisesUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrievePromisesUseCase.class);

    private final LicenseRepository repository;
    private final Clock clock;

    public RetrievePromisesUseCase(LicenseRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Mono<List<PromiseResponse>> execute(UUID organisationId, Integer changedWithinDays, Integer limit, String role) {
        log.info("[USE CASE] Retrieving the promises of the Licenses of organisation: {}", organisationId);
        return Mono.fromRunnable(() -> LicenseAccess.requireStaff(role, "list the promises of licences"))
                .then(Mono.fromCallable(() -> new int[]{PromiseSelection.days(changedWithinDays), PromiseSelection.limit(limit)}))
                .flatMap(window -> {
                    Instant now = Instant.now(clock);
                    return repository.findAll(organisationId).map(license -> toResponse(license, now)).collectList()
                            .map(all -> PromiseSelection.select(all, now.minus(Duration.ofDays(window[0])), window[1]));
                });
    }

    private static PromiseResponse toResponse(License license, Instant now) {
        Promise promise = license.promise(now);
        return new PromiseResponse(license.getId(), license.getProduct(), null, promise.verdict().name(), promise.code(), license.getStatus().name(), license.changedAt());
    }
}
