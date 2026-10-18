package com.thinklab.infrastructure.adapter.out.persistence.entity;

import com.thinklab.domain.model.UsageReport;
import io.micronaut.core.annotation.Introspected;
import org.bson.codecs.pojo.annotations.BsonId;

import java.time.Instant;
import java.util.UUID;

/** Infrastructure-specific representation of the UsageReport Aggregate for MongoDB. Immutable facts: only ever inserted, and expired by a TTL index on {@code measuredAt}. */
@Introspected
public class UsageReportDocument {

    @BsonId
    private UUID id;

    private UUID organisationId;
    private UUID licenseId;
    private int inUse;
    private Instant measuredAt;
    private String reportedBy;
    private Instant reportedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganisationId() { return organisationId; }
    public void setOrganisationId(UUID organisationId) { this.organisationId = organisationId; }
    public UUID getLicenseId() { return licenseId; }
    public void setLicenseId(UUID licenseId) { this.licenseId = licenseId; }
    public int getInUse() { return inUse; }
    public void setInUse(int inUse) { this.inUse = inUse; }
    public Instant getMeasuredAt() { return measuredAt; }
    public void setMeasuredAt(Instant measuredAt) { this.measuredAt = measuredAt; }
    public String getReportedBy() { return reportedBy; }
    public void setReportedBy(String reportedBy) { this.reportedBy = reportedBy; }
    public Instant getReportedAt() { return reportedAt; }
    public void setReportedAt(Instant reportedAt) { this.reportedAt = reportedAt; }

    public static final class UsageReportPersistenceMapper {

        private UsageReportPersistenceMapper() { throw new UnsupportedOperationException(); }

        public static UsageReportDocument toDocument(UsageReport report) {
            UsageReportDocument doc = new UsageReportDocument();
            doc.setId(report.getId());
            doc.setOrganisationId(report.getOrganisationId());
            doc.setLicenseId(report.getLicenseId());
            doc.setInUse(report.getInUse());
            doc.setMeasuredAt(report.getMeasuredAt());
            doc.setReportedBy(report.getReportedBy());
            doc.setReportedAt(report.getReportedAt());
            return doc;
        }

        public static UsageReport toDomain(UsageReportDocument doc) {
            return UsageReport.reconstitute(doc.getId(), doc.getOrganisationId(), doc.getLicenseId(), doc.getInUse(), doc.getMeasuredAt(), doc.getReportedBy(), doc.getReportedAt());
        }
    }
}
