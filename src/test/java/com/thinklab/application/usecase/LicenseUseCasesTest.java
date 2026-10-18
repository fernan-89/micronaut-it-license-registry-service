package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiateLicenseRequest;
import com.thinklab.application.dto.request.ReportUsageRequest;
import com.thinklab.application.dto.request.UpdateLicenseRequest;
import com.thinklab.domain.exception.InvalidLicenseStatusException;
import com.thinklab.domain.exception.LicenseAccessDeniedException;
import com.thinklab.domain.exception.LicenseNotFoundException;
import com.thinklab.domain.exception.UsageReportNotFoundException;
import com.thinklab.domain.model.License;
import com.thinklab.domain.model.License.Level;
import com.thinklab.domain.model.License.LicenseStatus;
import com.thinklab.domain.model.License.Unit;
import com.thinklab.domain.model.UsageReport;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.LicenseRepository;
import com.thinklab.domain.repository.UsageReportRepository;
import com.thinklab.domain.repository.UsageReportRepository.Stored;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LicenseUseCasesTest {

    private static final String STAFF = "ENGINEER";
    private static final String REQUESTER = "REQUESTER";
    private static final UUID ELSEWHERE = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final UUID org = UUID.randomUUID();
    private final UUID vendor = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-18T12:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    private HashServicePort hash;
    private LicenseRepository licenses;
    private UsageReportRepository reports;

    @BeforeEach
    void setUp() {
        hash = mock(HashServicePort.class);
        licenses = mock(LicenseRepository.class);
        reports = mock(UsageReportRepository.class);
    }

    private License license() {
        return License.createNew(UUID.randomUUID(), org, "Office 365 E3", Unit.USER, 100, 90, 24, vendor, null, "op");
    }

    /** A licence with 100 seats whose latest count is {@code inUse}. */
    private License counted(String product, Unit unit, UUID licenseVendor, int inUse) {
        return License.reconstitute(UUID.randomUUID(), org, product, unit, 100, 90, 24, licenseVendor, null, LicenseStatus.ACTIVE, now, now, inUse, now, List.of());
    }

    private UsageReport report(UUID licenseId, int inUse, int daysAgo) {
        return UsageReport.createNew(UUID.randomUUID(), org, licenseId, inUse, now.minus(Duration.ofDays(daysAgo)), "tool", now);
    }

    private InitiateLicenseRequest initiateRequest() {
        return new InitiateLicenseRequest("Office 365 E3", Unit.USER, 100, 90, 24, vendor, null);
    }

    private UpdateLicenseRequest updateRequest() {
        return new UpdateLicenseRequest("Office 365 E5", Unit.DEVICE, 200, 80, 12, null, null);
    }

    private ReportUsageRequest report(UUID licenseId) {
        return new ReportUsageRequest(licenseId, 42, now.minusSeconds(60));
    }

    @Test
    @DisplayName("the access rule refuses a requester and lets staff and a missing role through; it is a utility class")
    void access() throws ReflectiveOperationException {
        LicenseAccess.requireStaff(STAFF, "x");
        LicenseAccess.requireStaff(null, "x");
        LicenseAccessDeniedException denied = assertThrows(LicenseAccessDeniedException.class, () -> LicenseAccess.requireStaff(REQUESTER, "do it"));
        assertTrue(denied.getMessage().contains("do it"));
        Constructor<LicenseAccess> constructor = LicenseAccess.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThrows(InvocationTargetException.class, constructor::newInstance);
    }

    @Test
    @DisplayName("initiate spends a sovereign id, stores the licence and answers it judged now (no count yet); a requester spends nothing")
    void initiate() {
        UUID id = UUID.randomUUID();
        when(hash.generateSovereignId("license-creation")).thenReturn(Mono.just(id));
        when(licenses.create(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        InitiateLicenseUseCase useCase = new InitiateLicenseUseCase(hash, licenses, clock);

        StepVerifier.create(useCase.execute(org, initiateRequest(), "op", STAFF)).assertNext(response -> {
            assertEquals(id, response.id());
            assertEquals("ACTIVE", response.status());
            assertEquals("NO_DATA", response.level());
            assertEquals("USER", response.unit());
            assertEquals(100, response.entitled());
            assertEquals(vendor, response.vendorId());
            assertNull(response.available());
        }).verifyComplete();

        StepVerifier.create(useCase.execute(org, initiateRequest(), "op", REQUESTER)).expectError(LicenseAccessDeniedException.class).verify();
        verify(hash).generateSovereignId("license-creation");
    }

    @Test
    @DisplayName("retrieve answers the tenant's licence judged now, a 404 otherwise, and refuses a requester")
    void retrieve() {
        License license = counted("Office", Unit.USER, null, 95);
        when(licenses.findById(license.getId(), org)).thenReturn(Mono.just(license));
        when(licenses.findById(any(), eq(ELSEWHERE))).thenReturn(Mono.empty());
        RetrieveLicenseUseCase useCase = new RetrieveLicenseUseCase(licenses, clock);

        StepVerifier.create(useCase.execute(license.getId(), org, STAFF)).assertNext(response -> {
            assertEquals("NEAR_LIMIT", response.level());
            assertEquals(95.0, response.usagePercent());
            assertEquals(5, response.available());
            assertEquals(95, response.lastInUse());
            assertFalse(response.stale());
        }).verifyComplete();
        StepVerifier.create(useCase.execute(license.getId(), ELSEWHERE, STAFF)).expectError(LicenseNotFoundException.class).verify();
        StepVerifier.create(useCase.execute(license.getId(), org, REQUESTER)).expectError(LicenseAccessDeniedException.class).verify();
    }

    @Test
    @DisplayName("the list keeps the order and filters by status, unit, vendor and the level judged now")
    void list() {
        License office = counted("Office", Unit.USER, vendor, 50);
        License cad = counted("CAD", Unit.DEVICE, null, 120);
        License paused = counted("Old", Unit.USER, vendor, 10);
        paused.pause("op");
        when(licenses.findAll(org)).thenReturn(Flux.just(office, cad, paused));
        RetrieveLicensesUseCase useCase = new RetrieveLicensesUseCase(licenses, clock);

        StepVerifier.create(useCase.execute(org, null, null, null, null, STAFF).map(r -> r.product())).expectNext("Office", "CAD", "Old").verifyComplete();
        StepVerifier.create(useCase.execute(org, LicenseStatus.PAUSED, null, null, null, null).map(r -> r.product())).expectNext("Old").verifyComplete();
        StepVerifier.create(useCase.execute(org, null, Unit.DEVICE, null, null, STAFF).map(r -> r.product())).expectNext("CAD").verifyComplete();
        StepVerifier.create(useCase.execute(org, null, null, vendor, null, STAFF).map(r -> r.product())).expectNext("Office", "Old").verifyComplete();
        StepVerifier.create(useCase.execute(org, null, null, null, Level.OVER_LIMIT, STAFF).map(r -> r.product())).expectNext("CAD").verifyComplete();
        StepVerifier.create(useCase.execute(org, null, null, null, Level.NO_DATA, STAFF)).verifyComplete();
        StepVerifier.create(useCase.execute(org, null, null, null, null, REQUESTER)).expectError(LicenseAccessDeniedException.class).verify();
    }

    @Test
    @DisplayName("update, pause and resume go through the workflow with the status loaded; a requester and an unknown licence are refused")
    void updateAndControl() {
        License license = license();
        when(licenses.findById(license.getId(), org)).thenReturn(Mono.just(license));
        when(licenses.findById(any(), eq(ELSEWHERE))).thenReturn(Mono.empty());
        when(licenses.save(any(), any(), any())).thenReturn(Mono.empty());
        LicenseWorkflow workflow = new LicenseWorkflow(licenses);
        UpdateLicenseUseCase update = new UpdateLicenseUseCase(workflow);
        ControlLicenseUseCase control = new ControlLicenseUseCase(workflow);

        StepVerifier.create(update.execute(license.getId(), org, updateRequest(), "op", STAFF)).verifyComplete();
        assertEquals("Office 365 E5", license.getProduct());
        assertEquals(200, license.getEntitled());
        assertNull(license.getVendorId());
        StepVerifier.create(control.execute(license.getId(), org, ControlLicenseUseCase.Action.PAUSE, "op", null)).verifyComplete();
        assertEquals(LicenseStatus.PAUSED, license.getStatus());
        StepVerifier.create(control.execute(license.getId(), org, ControlLicenseUseCase.Action.RESUME, "op", STAFF)).verifyComplete();
        assertEquals(LicenseStatus.ACTIVE, license.getStatus());
        verify(licenses).save(any(), eq(LicenseStatus.PAUSED), any());
        StepVerifier.create(control.execute(license.getId(), org, ControlLicenseUseCase.Action.RESUME, "op", STAFF)).expectError(InvalidLicenseStatusException.class).verify();
        StepVerifier.create(update.execute(license.getId(), ELSEWHERE, updateRequest(), "op", STAFF)).expectError(LicenseNotFoundException.class).verify();
        StepVerifier.create(update.execute(license.getId(), org, updateRequest(), "op", REQUESTER)).expectError(LicenseAccessDeniedException.class).verify();
        StepVerifier.create(control.execute(license.getId(), org, ControlLicenseUseCase.Action.PAUSE, "op", REQUESTER)).expectError(LicenseAccessDeniedException.class).verify();
    }

    @Test
    @DisplayName("the audit log is the license's trail, a 404 for an unknown license, and staff only")
    void auditLog() {
        License license = license();
        license.pause("op");
        when(licenses.findById(license.getId(), org)).thenReturn(Mono.just(license));
        when(licenses.findById(any(), eq(ELSEWHERE))).thenReturn(Mono.empty());
        RetrieveLicenseAuditLogUseCase useCase = new RetrieveLicenseAuditLogUseCase(licenses);

        StepVerifier.create(useCase.execute(license.getId(), org, STAFF)).assertNext(entries -> {
            assertEquals(2, entries.size());
            assertEquals("INITIATED", entries.get(0).action());
        }).verifyComplete();
        StepVerifier.create(useCase.execute(license.getId(), ELSEWHERE, STAFF)).expectError(LicenseNotFoundException.class).verify();
        StepVerifier.create(useCase.execute(license.getId(), org, REQUESTER)).expectError(LicenseAccessDeniedException.class).verify();
    }

    @Test
    @DisplayName("a new report is stored and folded into the license; the answer says it was created")
    void reportNew() {
        License license = license();
        UUID sampleId = UUID.randomUUID();
        when(licenses.findById(license.getId(), org)).thenReturn(Mono.just(license));
        when(reports.findByNaturalKey(eq(org), eq(license.getId()), any())).thenReturn(Mono.empty());
        when(hash.generateSovereignId("usage-report-creation")).thenReturn(Mono.just(sampleId));
        when(reports.create(any())).thenAnswer(invocation -> Mono.just(new Stored(invocation.getArgument(0), true)));
        when(licenses.recordReport(eq(license.getId()), eq(org), any())).thenReturn(Mono.empty());
        ReportUsageUseCase useCase = new ReportUsageUseCase(hash, licenses, reports, clock);

        StepVerifier.create(useCase.execute(org, report(license.getId()), "tool", STAFF)).assertNext(reported -> {
            assertTrue(reported.created());
            assertEquals(sampleId, reported.report().id());
            assertEquals(42, reported.report().inUse());
            assertEquals("tool", reported.report().reportedBy());
        }).verifyComplete();
        verify(licenses).recordReport(eq(license.getId()), eq(org), any(UsageReport.class));
    }

    @Test
    @DisplayName("the same report reported again is the stored one, no id is spent, and the license's latest report is folded again")
    void reportAgain() {
        License license = license();
        UsageReport stored = report(license.getId(), 42, 0);
        when(licenses.findById(license.getId(), org)).thenReturn(Mono.just(license));
        when(reports.findByNaturalKey(eq(org), eq(license.getId()), any())).thenReturn(Mono.just(stored));
        when(licenses.recordReport(any(), any(), any())).thenReturn(Mono.empty());
        ReportUsageUseCase useCase = new ReportUsageUseCase(hash, licenses, reports, clock);

        StepVerifier.create(useCase.execute(org, report(license.getId()), "tool", STAFF)).assertNext(reported -> {
            assertFalse(reported.created());
            assertEquals(stored.getId(), reported.report().id());
        }).verifyComplete();
        verify(hash, never()).generateSovereignId(any());
        verify(reports, never()).create(any());
        verify(licenses).recordReport(eq(license.getId()), eq(org), eq(stored));
    }

    @Test
    @DisplayName("the lookup uses the moment cut to the millisecond, like the stored one")
    void reportLooksUpTheTruncatedMoment() {
        License license = license();
        when(licenses.findById(license.getId(), org)).thenReturn(Mono.just(license));
        when(reports.findByNaturalKey(any(), any(), any())).thenReturn(Mono.just(report(license.getId(), 1, 0)));
        when(licenses.recordReport(any(), any(), any())).thenReturn(Mono.empty());
        ReportUsageUseCase useCase = new ReportUsageUseCase(hash, licenses, reports, clock);
        Instant precise = Instant.parse("2026-10-18T11:00:00.123456789Z");

        StepVerifier.create(useCase.execute(org, new ReportUsageRequest(license.getId(), 1, precise), "tool", STAFF)).expectNextCount(1).verifyComplete();

        verify(reports).findByNaturalKey(org, license.getId(), Instant.parse("2026-10-18T11:00:00.123Z"));
    }

    @Test
    @DisplayName("a report for a license that is not the tenant's is a 404, a requester is refused, and a report from the future is a 400")
    void reportRefused() {
        UUID unknown = UUID.randomUUID();
        when(licenses.findById(unknown, org)).thenReturn(Mono.empty());
        License license = license();
        when(licenses.findById(license.getId(), org)).thenReturn(Mono.just(license));
        when(reports.findByNaturalKey(any(), any(), any())).thenReturn(Mono.empty());
        when(hash.generateSovereignId(any())).thenReturn(Mono.just(UUID.randomUUID()));
        ReportUsageUseCase useCase = new ReportUsageUseCase(hash, licenses, reports, clock);

        StepVerifier.create(useCase.execute(org, report(unknown), "tool", STAFF)).expectError(LicenseNotFoundException.class).verify();
        StepVerifier.create(useCase.execute(org, report(unknown), "tool", REQUESTER)).expectError(LicenseAccessDeniedException.class).verify();
        ReportUsageRequest future = new ReportUsageRequest(license.getId(), 1, now.plus(Duration.ofHours(1)));
        StepVerifier.create(useCase.execute(org, future, "tool", STAFF)).expectError(IllegalArgumentException.class).verify();
        verify(reports, never()).create(any());
    }

    @Test
    @DisplayName("one report: answered for the tenant, a 404 otherwise, staff only")
    void retrieveReport() {
        UsageReport stored = report(UUID.randomUUID(), 5, 0);
        when(reports.findById(stored.getId(), org)).thenReturn(Mono.just(stored));
        when(reports.findById(any(), eq(ELSEWHERE))).thenReturn(Mono.empty());
        RetrieveUsageReportUseCase useCase = new RetrieveUsageReportUseCase(reports);

        StepVerifier.create(useCase.execute(stored.getId(), org, STAFF)).assertNext(response -> assertEquals(5, response.inUse())).verifyComplete();
        StepVerifier.create(useCase.execute(stored.getId(), ELSEWHERE, STAFF)).expectError(UsageReportNotFoundException.class).verify();
        StepVerifier.create(useCase.execute(stored.getId(), org, REQUESTER)).expectError(LicenseAccessDeniedException.class).verify();
    }

    @Test
    @DisplayName("the report list uses 50 unless asked, accepts 1 to 200, refuses anything else and a requester")
    void retrieveReports() {
        UUID licenseId = UUID.randomUUID();
        when(reports.findAll(eq(org), eq(licenseId), any(Integer.class))).thenReturn(Flux.just(report(licenseId, 5, 0)));
        RetrieveUsageReportsUseCase useCase = new RetrieveUsageReportsUseCase(reports);

        StepVerifier.create(useCase.execute(org, licenseId, null, STAFF)).expectNextCount(1).verifyComplete();
        verify(reports).findAll(org, licenseId, 50);
        StepVerifier.create(useCase.execute(org, licenseId, 1, STAFF)).expectNextCount(1).verifyComplete();
        StepVerifier.create(useCase.execute(org, licenseId, 200, STAFF)).expectNextCount(1).verifyComplete();
        verify(reports).findAll(org, licenseId, 200);
        StepVerifier.create(useCase.execute(org, licenseId, 0, STAFF)).expectError(IllegalArgumentException.class).verify();
        StepVerifier.create(useCase.execute(org, licenseId, 201, STAFF)).expectError(IllegalArgumentException.class).verify();
        StepVerifier.create(useCase.execute(org, licenseId, null, REQUESTER)).expectError(LicenseAccessDeniedException.class).verify();
    }
}
