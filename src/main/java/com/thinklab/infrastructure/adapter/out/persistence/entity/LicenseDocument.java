package com.thinklab.infrastructure.adapter.out.persistence.entity;

import com.thinklab.domain.model.License;
import com.thinklab.domain.model.License.Unit;
import com.thinklab.domain.model.License.LicenseAuditEntry;
import com.thinklab.domain.model.License.LicenseStatus;
import io.micronaut.core.annotation.Introspected;
import org.bson.codecs.pojo.annotations.BsonId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Infrastructure-specific representation of the License Aggregate for MongoDB. */
@Introspected
public class LicenseDocument {

    @BsonId
    private UUID id;
    private UUID organisationId;
    private String product;
    private String unit;
    private int entitled;
    private int warnPercent;
    private int reportEveryHours;
    private UUID vendorId;
    private UUID agreementId;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
    private Integer lastInUse;
    private Instant lastMeasuredAt;
    private List<AuditEntryDocument> auditTrail = new ArrayList<>();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganisationId() { return organisationId; }
    public void setOrganisationId(UUID organisationId) { this.organisationId = organisationId; }
    public String getProduct() { return product; }
    public void setProduct(String product) { this.product = product; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public int getEntitled() { return entitled; }
    public void setEntitled(int entitled) { this.entitled = entitled; }
    public int getWarnPercent() { return warnPercent; }
    public void setWarnPercent(int warnPercent) { this.warnPercent = warnPercent; }
    public int getReportEveryHours() { return reportEveryHours; }
    public void setReportEveryHours(int reportEveryHours) { this.reportEveryHours = reportEveryHours; }
    public UUID getVendorId() { return vendorId; }
    public void setVendorId(UUID vendorId) { this.vendorId = vendorId; }
    public UUID getAgreementId() { return agreementId; }
    public void setAgreementId(UUID agreementId) { this.agreementId = agreementId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Integer getLastInUse() { return lastInUse; }
    public void setLastInUse(Integer lastInUse) { this.lastInUse = lastInUse; }
    public Instant getLastMeasuredAt() { return lastMeasuredAt; }
    public void setLastMeasuredAt(Instant lastMeasuredAt) { this.lastMeasuredAt = lastMeasuredAt; }
    public List<AuditEntryDocument> getAuditTrail() { return auditTrail; }
    public void setAuditTrail(List<AuditEntryDocument> auditTrail) { this.auditTrail = auditTrail; }

    @Introspected
    public record AuditEntryDocument(Instant occurredAt, String action, String executor, String fromStatus, String toStatus, String detail) {

        public static AuditEntryDocument fromDomain(LicenseAuditEntry entry) {
            return new AuditEntryDocument(entry.occurredAt(), entry.action(), entry.executor(), entry.fromStatus() != null ? entry.fromStatus().name() : null,
                    entry.toStatus().name(), entry.detail());
        }

        // toStatus has no null branch: fromDomain always writes entry.toStatus().name().
        LicenseAuditEntry toDomain() {
            return new LicenseAuditEntry(occurredAt, action, executor, fromStatus != null ? LicenseStatus.valueOf(fromStatus) : null, LicenseStatus.valueOf(toStatus), detail);
        }
    }

    public static final class LicensePersistenceMapper {

        private LicensePersistenceMapper() { throw new UnsupportedOperationException(); }

        public static LicenseDocument toDocument(License license) {
            LicenseDocument doc = new LicenseDocument();
            doc.setId(license.getId());
            doc.setOrganisationId(license.getOrganisationId());
            doc.setProduct(license.getProduct());
            doc.setUnit(license.getUnit().name());
            doc.setEntitled(license.getEntitled());
            doc.setWarnPercent(license.getWarnPercent());
            doc.setReportEveryHours(license.getReportEveryHours());
            doc.setVendorId(license.getVendorId());
            doc.setAgreementId(license.getAgreementId());
            doc.setStatus(license.getStatus().name());
            doc.setCreatedAt(license.getCreatedAt());
            doc.setUpdatedAt(license.getUpdatedAt());
            doc.setLastInUse(license.getLastInUse());
            doc.setLastMeasuredAt(license.getLastMeasuredAt());
            doc.setAuditTrail(license.getAuditTrail().stream().map(AuditEntryDocument::fromDomain).collect(Collectors.toCollection(ArrayList::new)));
            return doc;
        }

        public static License toDomain(LicenseDocument doc) {
            return License.reconstitute(doc.getId(), doc.getOrganisationId(), doc.getProduct(), Unit.valueOf(doc.getUnit()), doc.getEntitled(), doc.getWarnPercent(),
                    doc.getReportEveryHours(), doc.getVendorId(), doc.getAgreementId(), LicenseStatus.valueOf(doc.getStatus()), doc.getCreatedAt(), doc.getUpdatedAt(),
                    doc.getLastInUse(), doc.getLastMeasuredAt(), doc.getAuditTrail().stream().map(AuditEntryDocument::toDomain).collect(Collectors.toList()));
        }
    }
}
