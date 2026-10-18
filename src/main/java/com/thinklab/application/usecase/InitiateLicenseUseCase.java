package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiateLicenseRequest;
import com.thinklab.application.dto.response.LicenseResponse;
import com.thinklab.application.mapper.LicenseMapper;
import com.thinklab.domain.model.License;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.LicenseRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Use Case for registering the seats bought of a product (BIAN Behavior Qualifier: {@code initiate}). Staff only. */
@Singleton
public class InitiateLicenseUseCase {

    private static final Logger log = LoggerFactory.getLogger(InitiateLicenseUseCase.class);

    private final HashServicePort hashServicePort;
    private final LicenseRepository repository;
    private final Clock clock;

    public InitiateLicenseUseCase(HashServicePort hashServicePort, LicenseRepository repository, Clock clock) {
        this.hashServicePort = hashServicePort;
        this.repository = repository;
        this.clock = clock;
    }

    public Mono<LicenseResponse> execute(UUID organisationId, InitiateLicenseRequest request, String executor, String role) {
        log.info("[USE CASE] Creating the License [{}] for organisation: {}", request.product(), organisationId);

        return Mono.fromRunnable(() -> LicenseAccess.requireStaff(role, "register a licence"))
                .then(Mono.defer(() -> hashServicePort.generateSovereignId("license-creation")))
                .map(id -> License.createNew(id, organisationId, request.product(), request.unit(), request.entitled(), request.warnPercent(), request.reportEveryHours(),
                        request.vendorId(), request.agreementId(), executor))
                .flatMap(repository::create)
                .map(license -> LicenseMapper.toResponse(license, license.assess(Instant.now(clock))));
    }
}
