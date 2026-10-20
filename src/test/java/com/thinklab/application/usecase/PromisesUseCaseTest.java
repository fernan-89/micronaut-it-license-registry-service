package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.PromiseResponse;
import com.thinklab.domain.exception.LicenseAccessDeniedException;
import com.thinklab.domain.model.License;
import com.thinklab.domain.model.License.LicenseStatus;
import com.thinklab.domain.model.License.Unit;
import com.thinklab.domain.repository.LicenseRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PromisesUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");
    private final UUID org = UUID.randomUUID();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private License licence(String product, int inUse, Instant measured) {
        return License.reconstitute(UUID.randomUUID(), org, product, Unit.USER, 100, 90, 24, null, null, LicenseStatus.ACTIVE, NOW.minus(Duration.ofDays(90)), NOW.minus(Duration.ofDays(90)),
                inUse, measured, List.of());
    }

    private RetrievePromisesUseCase useCase(License... found) {
        LicenseRepository repository = mock(LicenseRepository.class);
        when(repository.findAll(org)).thenReturn(Flux.just(found));
        return new RetrievePromisesUseCase(repository, clock);
    }

    @Test
    @DisplayName("a licence over its limit is broken with LICENSE_OVER_LIMIT; one counted lately is listed as kept with the count's moment; a quiet one is left out; the name is the product and there is no asset")
    void selects() {
        License over = licence("AutoCAD", 120, NOW.minus(Duration.ofDays(60)));
        License recovered = licence("Office", 50, NOW.minus(Duration.ofHours(2)));
        License quiet = licence("Visio", 50, NOW.minus(Duration.ofDays(60)));

        StepVerifier.create(useCase(quiet, recovered, over).execute(org, null, null, "ENGINEER")).assertNext(promises -> {
            assertEquals(List.of("AutoCAD", "Office"), promises.stream().map(PromiseResponse::name).toList());
            assertEquals("BROKEN", promises.get(0).verdict());
            assertEquals("LICENSE_OVER_LIMIT", promises.get(0).code());
            assertNull(promises.get(0).assetId());
            assertEquals("ACTIVE", promises.get(0).status());
            assertEquals("KEPT", promises.get(1).verdict());
            assertEquals(NOW.minus(Duration.ofHours(2)), promises.get(1).changedAt());
        }).verifyComplete();
    }

    @Test
    @DisplayName("the window and the limit apply to each kind")
    void window() {
        License keptOld = licence("OLD", 50, NOW.minus(Duration.ofHours(30)));
        License kept = licence("NEW", 50, NOW.minus(Duration.ofHours(5)));
        License one = licence("B1", 120, NOW.minus(Duration.ofDays(60)));
        License two = licence("B2", 120, NOW.minus(Duration.ofDays(60)));

        StepVerifier.create(useCase(keptOld, kept, one, two).execute(org, 1, 1, "ENGINEER"))
                .assertNext(promises -> assertEquals(List.of("B1", "NEW"), promises.stream().map(PromiseResponse::name).toList())).verifyComplete();
    }

    @Test
    @DisplayName("the days must be 1 to 365 and the limit 1 to 1000; a requester is refused")
    void validation() {
        RetrievePromisesUseCase useCase = useCase();

        assertThrows(IllegalArgumentException.class, () -> useCase.execute(org, 0, null, "ENGINEER").block());
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(org, 366, null, "ENGINEER").block());
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(org, null, 0, "ENGINEER").block());
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(org, null, 1001, "ENGINEER").block());
        assertThrows(LicenseAccessDeniedException.class, () -> useCase.execute(org, null, null, "REQUESTER").block());
    }

    @Test
    @DisplayName("the selection is a utility class")
    void utility() throws ReflectiveOperationException {
        Constructor<PromiseSelection> constructor = PromiseSelection.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThrows(InvocationTargetException.class, constructor::newInstance);
    }
}
