package com.thinklab.infrastructure.adapter.in.web;

import com.thinklab.application.dto.request.InitiateLicenseRequest;
import com.thinklab.application.dto.request.ReportUsageRequest;
import com.thinklab.application.dto.request.UpdateLicenseRequest;
import com.thinklab.application.dto.response.AuditEntryResponse;
import com.thinklab.application.dto.response.LicenseResponse;
import com.thinklab.application.dto.response.UsageReportResponse;
import com.thinklab.application.usecase.ControlLicenseUseCase;
import com.thinklab.application.usecase.InitiateLicenseUseCase;
import com.thinklab.application.usecase.ReportUsageUseCase;
import com.thinklab.application.usecase.ReportUsageUseCase.Reported;
import com.thinklab.application.usecase.RetrieveLicensesUseCase;
import com.thinklab.application.usecase.RetrieveLicenseAuditLogUseCase;
import com.thinklab.application.usecase.RetrieveLicenseUseCase;
import com.thinklab.application.usecase.RetrieveUsageReportUseCase;
import com.thinklab.application.usecase.RetrieveUsageReportsUseCase;
import com.thinklab.application.usecase.UpdateLicenseUseCase;
import com.thinklab.domain.model.License.Level;
import com.thinklab.domain.model.License.Unit;
import com.thinklab.domain.model.License.LicenseStatus;
import io.micronaut.http.HttpStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/** The controller only reads headers and delegates: tenant, executor and role always travel to the use case. */
@ExtendWith(MockitoExtension.class)
class LicenseRegistryControllerTest {

    private static final String EXECUTOR = "op-1";
    private static final String ROLE = "ENGINEER";
    private final UUID tenant = UUID.randomUUID();
    private final String tenantHeader = tenant.toString();
    private final UUID id = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-18T12:00:00Z");

    @Mock private InitiateLicenseUseCase initiateLicense;
    @Mock private RetrieveLicenseUseCase retrieveLicense;
    @Mock private RetrieveLicensesUseCase retrieveLicenses;
    @Mock private UpdateLicenseUseCase updateLicense;
    @Mock private ControlLicenseUseCase controlLicense;
    @Mock private RetrieveLicenseAuditLogUseCase licenseAuditLog;
    @Mock private ReportUsageUseCase reportUsage;
    @Mock private RetrieveUsageReportUseCase retrieveReport;
    @Mock private RetrieveUsageReportsUseCase retrieveReports;

    private LicenseRegistryController controller() {
        return new LicenseRegistryController(initiateLicense, retrieveLicense, retrieveLicenses, updateLicense, controlLicense, licenseAuditLog, reportUsage, retrieveReport, retrieveReports);
    }

    private LicenseResponse licenseResponse() {
        return new LicenseResponse(id, tenant, "Office", "USER", 100, 90, 24, null, null, "ACTIVE", "OK", 50.0, 50, false, 50, now, now, now);
    }

    private UsageReportResponse reportResponse() {
        return new UsageReportResponse(id, tenant, UUID.randomUUID(), 42, now, "tool", now);
    }

    @Test
    @DisplayName("initiate answers 201 with the license")
    void initiate() {
        InitiateLicenseRequest request = new InitiateLicenseRequest("Office", Unit.USER, 100, 90, 24, null, null);
        when(initiateLicense.execute(tenant, request, EXECUTOR, ROLE)).thenReturn(Mono.just(licenseResponse()));

        StepVerifier.create(controller().initiate(tenantHeader, EXECUTOR, ROLE, request)).assertNext(response -> assertEquals(HttpStatus.CREATED, response.getStatus())).verifyComplete();
    }

    @Test
    @DisplayName("retrieve and the list answer the use cases' result with the filters")
    void retrieve() {
        when(retrieveLicense.execute(id, tenant, ROLE)).thenReturn(Mono.just(licenseResponse()));
        when(retrieveLicenses.execute(tenant, LicenseStatus.ACTIVE, Unit.USER, null, Level.OK, ROLE)).thenReturn(Flux.just(licenseResponse()));

        StepVerifier.create(controller().retrieve(id, tenantHeader, ROLE)).assertNext(response -> assertEquals(HttpStatus.OK, response.getStatus())).verifyComplete();
        StepVerifier.create(controller().retrieveAll(tenantHeader, ROLE, LicenseStatus.ACTIVE, Unit.USER, null, Level.OK)).assertNext(list -> assertEquals(1, list.size())).verifyComplete();
    }

    @Test
    @DisplayName("update, pause and resume answer 204 and pass the right action")
    void changes() {
        UpdateLicenseRequest request = new UpdateLicenseRequest("N", Unit.CORE, 8, 60, 6, UUID.randomUUID(), null);
        when(updateLicense.execute(id, tenant, request, EXECUTOR, ROLE)).thenReturn(Mono.empty());
        when(controlLicense.execute(id, tenant, ControlLicenseUseCase.Action.PAUSE, EXECUTOR, ROLE)).thenReturn(Mono.empty());
        when(controlLicense.execute(id, tenant, ControlLicenseUseCase.Action.RESUME, EXECUTOR, ROLE)).thenReturn(Mono.empty());

        StepVerifier.create(controller().update(id, tenantHeader, EXECUTOR, ROLE, request)).assertNext(response -> assertEquals(HttpStatus.NO_CONTENT, response.getStatus())).verifyComplete();
        StepVerifier.create(controller().pause(id, tenantHeader, EXECUTOR, ROLE)).assertNext(response -> assertEquals(HttpStatus.NO_CONTENT, response.getStatus())).verifyComplete();
        StepVerifier.create(controller().resume(id, tenantHeader, EXECUTOR, ROLE)).assertNext(response -> assertEquals(HttpStatus.NO_CONTENT, response.getStatus())).verifyComplete();
    }

    @Test
    @DisplayName("the audit log answers the trail")
    void auditLog() {
        when(licenseAuditLog.execute(id, tenant, ROLE)).thenReturn(Mono.just(List.of(new AuditEntryResponse(now, "INITIATED", EXECUTOR, null, "ACTIVE", null))));

        StepVerifier.create(controller().auditLog(id, tenantHeader, ROLE)).assertNext(list -> assertEquals(1, list.size())).verifyComplete();
    }

    @Test
    @DisplayName("a new report is 201 and one already reported is 200")
    void reportUsageStatus() {
        ReportUsageRequest request = new ReportUsageRequest(UUID.randomUUID(), 42, now);
        when(reportUsage.execute(tenant, request, EXECUTOR, ROLE)).thenReturn(Mono.just(new Reported(reportResponse(), true))).thenReturn(Mono.just(new Reported(reportResponse(), false)));

        StepVerifier.create(controller().reportUsage(tenantHeader, EXECUTOR, ROLE, request)).assertNext(response -> assertEquals(HttpStatus.CREATED, response.getStatus())).verifyComplete();
        StepVerifier.create(controller().reportUsage(tenantHeader, EXECUTOR, ROLE, request)).assertNext(response -> assertEquals(HttpStatus.OK, response.getStatus())).verifyComplete();
    }

    @Test
    @DisplayName("one report and the report list, with the licence and the limit")
    void runs() {
        UUID licenseId = UUID.randomUUID();
        when(retrieveReport.execute(id, tenant, ROLE)).thenReturn(Mono.just(reportResponse()));
        when(retrieveReports.execute(tenant, licenseId, 10, ROLE)).thenReturn(Flux.just(reportResponse()));

        StepVerifier.create(controller().retrieveReport(id, tenantHeader, ROLE)).assertNext(response -> assertEquals(HttpStatus.OK, response.getStatus())).verifyComplete();
        StepVerifier.create(controller().retrieveReports(tenantHeader, ROLE, licenseId, 10)).assertNext(list -> assertEquals(1, list.size())).verifyComplete();
    }
}
