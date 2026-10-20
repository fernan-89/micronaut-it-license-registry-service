package com.thinklab.infrastructure.adapter.in.web;

import com.thinklab.application.dto.request.InitiateLicenseRequest;
import com.thinklab.application.dto.request.ReportUsageRequest;
import com.thinklab.application.dto.request.UpdateLicenseRequest;
import com.thinklab.application.dto.response.PromiseResponse;
import com.thinklab.application.dto.response.AuditEntryResponse;
import com.thinklab.application.dto.response.LicenseResponse;
import com.thinklab.application.dto.response.UsageReportResponse;
import com.thinklab.application.usecase.RetrievePromisesUseCase;
import com.thinklab.application.usecase.ControlLicenseUseCase;
import com.thinklab.application.usecase.InitiateLicenseUseCase;
import com.thinklab.application.usecase.ReportUsageUseCase;
import com.thinklab.application.usecase.RetrieveLicensesUseCase;
import com.thinklab.application.usecase.RetrieveLicenseAuditLogUseCase;
import com.thinklab.application.usecase.RetrieveLicenseUseCase;
import com.thinklab.application.usecase.RetrieveUsageReportUseCase;
import com.thinklab.application.usecase.RetrieveUsageReportsUseCase;
import com.thinklab.application.usecase.UpdateLicenseUseCase;
import com.thinklab.domain.model.License.Level;
import com.thinklab.domain.model.License.Unit;
import com.thinklab.domain.model.License.LicenseStatus;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Inbound Web Adapter for the {@code it-license-registry} Service Domain.
 *
 * <p><b>BIAN-Aligned Resource Model (ADR-013):</b> {@link com.thinklab.domain.model.License} is the Control Record, at the root;
 * {@link com.thinklab.domain.model.UsageReport} is the secondary aggregate, under {@code /usage}: counts the tool reports, never edited, never
 * deleted. There is no {@code DELETE}: a license is paused.
 *
 * <p><b>Tenant on every route, staff only (ADR-032):</b> {@code X-Tenant-Id} scopes every lookup (another tenant's license is a 404) and
 * {@code X-Role} makes every route refuse a {@code REQUESTER} with 403 {@code ERR-LIC-00403}.
 */
@Controller("/it-license-registry/v1")
public class LicenseRegistryController {

    private static final Logger log = LoggerFactory.getLogger(LicenseRegistryController.class);
    static final String TENANT_HEADER = "X-Tenant-Id";
    static final String EXECUTOR_HEADER = "X-Executor";
    static final String ROLE_HEADER = "X-Role";

    private final InitiateLicenseUseCase initiateLicense;
    private final RetrieveLicenseUseCase retrieveLicense;
    private final RetrieveLicensesUseCase retrieveLicenses;
    private final UpdateLicenseUseCase updateLicense;
    private final ControlLicenseUseCase controlLicense;
    private final RetrieveLicenseAuditLogUseCase licenseAuditLog;
    private final ReportUsageUseCase reportUsage;
    private final RetrieveUsageReportUseCase retrieveReport;
    private final RetrieveUsageReportsUseCase retrieveReports;

    private final RetrievePromisesUseCase retrievePromises;

    public LicenseRegistryController(InitiateLicenseUseCase initiateLicense, RetrieveLicenseUseCase retrieveLicense, RetrieveLicensesUseCase retrieveLicenses,
                                    UpdateLicenseUseCase updateLicense, ControlLicenseUseCase controlLicense, RetrieveLicenseAuditLogUseCase licenseAuditLog,
                                    ReportUsageUseCase reportUsage, RetrieveUsageReportUseCase retrieveReport, RetrieveUsageReportsUseCase retrieveReports,
            RetrievePromisesUseCase retrievePromises) {
        this.retrievePromises = retrievePromises;
        this.initiateLicense = initiateLicense;
        this.retrieveLicense = retrieveLicense;
        this.retrieveLicenses = retrieveLicenses;
        this.updateLicense = updateLicense;
        this.controlLicense = controlLicense;
        this.licenseAuditLog = licenseAuditLog;
        this.reportUsage = reportUsage;
        this.retrieveReport = retrieveReport;
        this.retrieveReports = retrieveReports;
    }

    // ------------------------------------------------------------------ Licences

    /** Behavior Qualifier: {@code initiate}. How many seats of a product were bought. */
    @Post("/initiate")
    public Mono<HttpResponse<LicenseResponse>> initiate(
            @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Header(ROLE_HEADER) @Nullable String role, @Body @Valid InitiateLicenseRequest request
    ) {
        log.info("[ACTION: INITIATE_LICENSE] [EXECUTOR: {}] Received request for organisation: {}", executor, tenantId);

        return initiateLicense.execute(UUID.fromString(tenantId), request, executor, role).map(HttpResponse::created);
    }

    /** Behavior Qualifier: {@code retrieve}. One license of the tenant, judged now. */
    @Get("/{id}/retrieve")
    public Mono<HttpResponse<LicenseResponse>> retrieve(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(ROLE_HEADER) @Nullable String role) {
        return Mono.defer(() -> retrieveLicense.execute(id, UUID.fromString(tenantId), role)).map(HttpResponse::ok);
    }

    /** Behavior Qualifier: {@code retrieve} (collection), oldest first, filterable by status, unit, vendor and level. */
    @Get("/retrieve")
    public Mono<List<LicenseResponse>> retrieveAll(@Header(TENANT_HEADER) @NotBlank String tenantId, @Header(ROLE_HEADER) @Nullable String role,
                                                        @QueryValue @Nullable LicenseStatus status, @QueryValue @Nullable Unit unit, @QueryValue @Nullable UUID vendorId,
                                                        @QueryValue @Nullable Level level) {
        return Mono.defer(() -> retrieveLicenses.execute(UUID.fromString(tenantId), status, unit, vendorId, level, role).collectList());
    }

    /**
     * Behavior Qualifier: {@code promises/retrieve} (platform ADR-001, the promises contract). Every broken promise and every one that changed within the days asked
     * (30 unless asked, 1 to 365), at most {@code limit} of each (500 unless asked, at most 1000).
     */
    @Get("/promises/retrieve")
    public Mono<List<PromiseResponse>> promises(@Header(TENANT_HEADER) @NotBlank String tenantId, @Header(ROLE_HEADER) @Nullable String role,
                                                @QueryValue @Nullable Integer changedWithinDays, @QueryValue @Nullable Integer limit) {
        return Mono.defer(() -> retrievePromises.execute(UUID.fromString(tenantId), changedWithinDays, limit, role));
    }

    /** Behavior Qualifier: {@code update}. The whole definition again. */
    @Put("/{id}/update")
    public Mono<HttpResponse<Void>> update(
            @PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Header(ROLE_HEADER) @Nullable String role, @Body @Valid UpdateLicenseRequest request
    ) {
        return Mono.defer(() -> updateLicense.execute(id, UUID.fromString(tenantId), request, executor, role)).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code control/pause}. ACTIVE -&gt; PAUSED: the license is no longer judged. */
    @Put("/{id}/control/pause")
    public Mono<HttpResponse<Void>> pause(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId,
                                          @Header(EXECUTOR_HEADER) @NotBlank String executor, @Header(ROLE_HEADER) @Nullable String role) {
        return control(id, tenantId, ControlLicenseUseCase.Action.PAUSE, executor, role);
    }

    /** Behavior Qualifier: {@code control/resume}. PAUSED -&gt; ACTIVE. */
    @Put("/{id}/control/resume")
    public Mono<HttpResponse<Void>> resume(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId,
                                           @Header(EXECUTOR_HEADER) @NotBlank String executor, @Header(ROLE_HEADER) @Nullable String role) {
        return control(id, tenantId, ControlLicenseUseCase.Action.RESUME, executor, role);
    }

    /** Behavior Qualifier: {@code audit-log/retrieve}. */
    @Get("/{id}/audit-log/retrieve")
    public Mono<List<AuditEntryResponse>> auditLog(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(ROLE_HEADER) @Nullable String role) {
        return Mono.defer(() -> licenseAuditLog.execute(id, UUID.fromString(tenantId), role));
    }

    // ------------------------------------------------------------------ Usage reports

    /** Behavior Qualifier: {@code usage/initiate}. What the tool reports when it measures: 201 for a new report, 200 for one already reported. */
    @Post("/usage/initiate")
    public Mono<HttpResponse<UsageReportResponse>> reportUsage(
            @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Header(ROLE_HEADER) @Nullable String role, @Body @Valid ReportUsageRequest request
    ) {
        log.info("[ACTION: REPORT_USAGE] [EXECUTOR: {}] Received request for organisation: {}", executor, tenantId);

        return reportUsage.execute(UUID.fromString(tenantId), request, executor, role)
                .map(reported -> reported.created() ? HttpResponse.created(reported.report()) : HttpResponse.ok(reported.report()));
    }

    /** Behavior Qualifier: {@code usage/retrieve}. One report of the tenant. */
    @Get("/usage/{id}/retrieve")
    public Mono<HttpResponse<UsageReportResponse>> retrieveReport(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(ROLE_HEADER) @Nullable String role) {
        return Mono.defer(() -> retrieveReport.execute(id, UUID.fromString(tenantId), role)).map(HttpResponse::ok);
    }

    /** Behavior Qualifier: {@code usage/retrieve} (collection), newest first, at most 200 (50 unless asked). */
    @Get("/usage/retrieve")
    public Mono<List<UsageReportResponse>> retrieveReports(@Header(TENANT_HEADER) @NotBlank String tenantId, @Header(ROLE_HEADER) @Nullable String role,
                                                      @QueryValue @Nullable UUID licenseId, @QueryValue @Nullable Integer limit) {
        return Mono.defer(() -> retrieveReports.execute(UUID.fromString(tenantId), licenseId, limit, role).collectList());
    }

    private Mono<HttpResponse<Void>> control(UUID id, String tenantId, ControlLicenseUseCase.Action action, String executor, String role) {
        log.info("[ACTION: {}_LICENSE] [EXECUTOR: {}] for ID: {}", action, executor, id);

        return Mono.defer(() -> controlLicense.execute(id, UUID.fromString(tenantId), action, executor, role)).thenReturn(HttpResponse.noContent());
    }
}
