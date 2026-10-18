package com.thinklab.application.mapper;

import com.thinklab.application.dto.response.AuditEntryResponse;
import com.thinklab.application.dto.response.LicenseResponse;
import com.thinklab.application.dto.response.UsageReportResponse;
import com.thinklab.domain.model.License;
import com.thinklab.domain.model.License.Assessment;
import com.thinklab.domain.model.License.LicenseAuditEntry;
import com.thinklab.domain.model.UsageReport;

public final class LicenseMapper {

    private LicenseMapper() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /** The license as it stands, with how it was judged. */
    public static LicenseResponse toResponse(License license, Assessment judged) {
        return new LicenseResponse(license.getId(), license.getOrganisationId(), license.getProduct(), license.getUnit().name(), license.getEntitled(), license.getWarnPercent(),
                license.getReportEveryHours(), license.getVendorId(), license.getAgreementId(), license.getStatus().name(), judged.level().name(), judged.usagePercent(),
                judged.available(), judged.stale(), license.getLastInUse(), license.getLastMeasuredAt(), license.getCreatedAt(), license.getUpdatedAt());
    }

    public static UsageReportResponse toResponse(UsageReport report) {
        return new UsageReportResponse(report.getId(), report.getOrganisationId(), report.getLicenseId(), report.getInUse(), report.getMeasuredAt(), report.getReportedBy(),
                report.getReportedAt());
    }

    public static AuditEntryResponse toResponse(LicenseAuditEntry entry) {
        return new AuditEntryResponse(entry.occurredAt(), entry.action(), entry.executor(), entry.fromStatus() != null ? entry.fromStatus().name() : null,
                entry.toStatus().name(), entry.detail());
    }
}
