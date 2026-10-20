package com.thinklab.domain.model;

import com.thinklab.domain.exception.InvalidLicenseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate Root (BIAN Control Record) of the IT License Registry Service Domain: how many seats of a software product the organisation is
 * entitled to. A licence names the product, the {@link Unit} a seat is counted in (a user, a device, an install, a core), how many seats were
 * bought, the use at which to warn, and how often the inventory tool is expected to report. The tool counts the seats in use and reports the
 * count (ADR-030); the licence keeps only the latest one and judges itself when read (ADR-031). It may name the vendor and the agreement it was
 * bought under (vendor registry ids, not checked), and nothing about a person.
 */
public class License {

    public static final int MAX_PRODUCT_LENGTH = 80;
    public static final int MAX_ENTITLED = 10_000_000;
    public static final int MAX_REPORT_EVERY_HOURS = 720;

    private final UUID id;
    private final UUID organisationId;
    private String product;
    private Unit unit;
    private int entitled;
    private int warnPercent;
    private int reportEveryHours;
    private UUID vendorId;
    private UUID agreementId;
    private LicenseStatus status;
    private final Instant createdAt;
    private Instant updatedAt;
    private Integer lastInUse;
    private Instant lastMeasuredAt;
    private final List<LicenseAuditEntry> auditTrail;

    private License(UUID id, UUID organisationId, String product, Unit unit, int entitled, int warnPercent, int reportEveryHours, UUID vendorId, UUID agreementId,
                    String executor) {
        this.id = id;
        this.organisationId = organisationId;
        this.product = product;
        this.unit = unit;
        this.entitled = entitled;
        this.warnPercent = warnPercent;
        this.reportEveryHours = reportEveryHours;
        this.vendorId = vendorId;
        this.agreementId = agreementId;
        this.status = LicenseStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.auditTrail = new ArrayList<>();
        this.auditTrail.add(new LicenseAuditEntry(this.createdAt, "INITIATED", executor, null, LicenseStatus.ACTIVE, entitled + " " + unit + " seats of " + product + "."));
    }

    private License(UUID id, UUID organisationId, String product, Unit unit, int entitled, int warnPercent, int reportEveryHours, UUID vendorId, UUID agreementId,
                    LicenseStatus status, Instant createdAt, Instant updatedAt, Integer lastInUse, Instant lastMeasuredAt, List<LicenseAuditEntry> auditTrail) {
        this.id = id;
        this.organisationId = organisationId;
        this.product = product;
        this.unit = unit;
        this.entitled = entitled;
        this.warnPercent = warnPercent;
        this.reportEveryHours = reportEveryHours;
        this.vendorId = vendorId;
        this.agreementId = agreementId;
        this.status = status != null ? status : LicenseStatus.ACTIVE;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
        this.lastInUse = lastInUse;
        this.lastMeasuredAt = lastMeasuredAt;
        this.auditTrail = auditTrail != null ? new ArrayList<>(auditTrail) : new ArrayList<>();
    }

    public static License createNew(UUID id, UUID organisationId, String product, Unit unit, int entitled, int warnPercent, int reportEveryHours, UUID vendorId,
                                    UUID agreementId, String executor) {
        if (id == null || organisationId == null) {
            throw new IllegalArgumentException("ID and Organisation ID are mandatory for License creation.");
        }
        validate(product, unit, entitled, warnPercent, reportEveryHours);
        requireExecutor(executor);
        return new License(id, organisationId, product, unit, entitled, warnPercent, reportEveryHours, vendorId, agreementId, executor);
    }

    public static License reconstitute(UUID id, UUID organisationId, String product, Unit unit, int entitled, int warnPercent, int reportEveryHours, UUID vendorId,
                                       UUID agreementId, LicenseStatus status, Instant createdAt, Instant updatedAt, Integer lastInUse, Instant lastMeasuredAt,
                                       List<LicenseAuditEntry> auditTrail) {
        if (id == null || organisationId == null || product == null || unit == null) {
            throw new IllegalArgumentException("ID, Organisation ID, Product and Unit are mandatory to reconstitute a License.");
        }
        return new License(id, organisationId, product, unit, entitled, warnPercent, reportEveryHours, vendorId, agreementId, status, createdAt, updatedAt, lastInUse,
                lastMeasuredAt, auditTrail);
    }

    // --- Domain Behaviors ---

    /** Behavior Qualifier: {@code update}. The whole definition again, in any status; the latest report is not touched. */
    public LicenseAuditEntry update(String newProduct, Unit newUnit, int newEntitled, int newWarnPercent, int newReportEveryHours, UUID newVendorId, UUID newAgreementId,
                                    String executor) {
        validate(newProduct, newUnit, newEntitled, newWarnPercent, newReportEveryHours);
        requireExecutor(executor);
        this.product = newProduct;
        this.unit = newUnit;
        this.entitled = newEntitled;
        this.warnPercent = newWarnPercent;
        this.reportEveryHours = newReportEveryHours;
        this.vendorId = newVendorId;
        this.agreementId = newAgreementId;
        return record("UPDATED", executor, this.status, this.status, "Licence updated: " + newEntitled + " " + newUnit + " seats.");
    }

    /** Behavior Qualifier: {@code control/pause}. ACTIVE -&gt; PAUSED: the licence is no longer judged. */
    public LicenseAuditEntry pause(String executor) {
        requireStatus(LicenseStatus.ACTIVE);
        requireExecutor(executor);
        this.status = LicenseStatus.PAUSED;
        return record("PAUSED", executor, LicenseStatus.ACTIVE, LicenseStatus.PAUSED, "Paused: the licence is no longer judged.");
    }

    /** Behavior Qualifier: {@code control/resume}. PAUSED -&gt; ACTIVE. */
    public LicenseAuditEntry resume(String executor) {
        requireStatus(LicenseStatus.PAUSED);
        requireExecutor(executor);
        this.status = LicenseStatus.ACTIVE;
        return record("RESUMED", executor, LicenseStatus.PAUSED, LicenseStatus.ACTIVE, "Resumed.");
    }

    /**
     * Where the licence stands at that moment (ADR-031): the latest count against the seats bought. More in use than bought is OVER_LIMIT; from the
     * warning percentage on it is NEAR_LIMIT; below that OK. The count is stale when the tool is silent for more than twice its expected interval
     * (it is still judged on it: seats in use do not free themselves). Nothing of it is stored.
     */
    public Assessment assess(Instant now) {
        if (status == LicenseStatus.PAUSED) {
            return new Assessment(Level.PAUSED, null, null, false);
        }
        if (lastInUse == null) {
            return new Assessment(Level.NO_DATA, null, null, false);
        }
        double usage = lastInUse * 100.0 / entitled;
        Level level = lastInUse > entitled ? Level.OVER_LIMIT : usage >= warnPercent ? Level.NEAR_LIMIT : Level.OK;
        boolean stale = now.isAfter(lastMeasuredAt.plus(Duration.ofHours(2L * reportEveryHours)));
        return new Assessment(level, usage, entitled - lastInUse, stale);
    }

    // --- Internal helpers ---

    private LicenseAuditEntry record(String action, String executor, LicenseStatus from, LicenseStatus to, String detail) {
        this.updatedAt = Instant.now();
        LicenseAuditEntry entry = new LicenseAuditEntry(this.updatedAt, action, executor, from, to, detail);
        this.auditTrail.add(entry);
        return entry;
    }

    private void requireStatus(LicenseStatus expected) {
        if (this.status != expected) {
            throw new InvalidLicenseStatusException(String.format("Illegal transition: License is [%s], expected [%s].", this.status, expected));
        }
    }

    private static void requireExecutor(String executor) {
        if (executor == null || executor.isBlank()) {
            throw new IllegalArgumentException("Executor is mandatory for auditable License mutations.");
        }
    }

    private static void validate(String product, Unit unit, int entitled, int warnPercent, int reportEveryHours) {
        if (product == null || product.isBlank() || product.length() > MAX_PRODUCT_LENGTH) {
            throw new IllegalArgumentException("The product is mandatory for a License (up to " + MAX_PRODUCT_LENGTH + " characters).");
        }
        if (unit == null) {
            throw new IllegalArgumentException("The unit a seat is counted in is mandatory.");
        }
        if (entitled < 1 || entitled > MAX_ENTITLED) {
            throw new IllegalArgumentException("The seats bought must be between 1 and " + MAX_ENTITLED + ".");
        }
        if (warnPercent < 1 || warnPercent > 100) {
            throw new IllegalArgumentException("The warning use must be between 1 and 100 percent.");
        }
        if (reportEveryHours < 1 || reportEveryHours > MAX_REPORT_EVERY_HOURS) {
            throw new IllegalArgumentException("The expected reporting interval must be between 1 and " + MAX_REPORT_EVERY_HOURS + " hours.");
        }
    }

    // --- Getters ---

    public UUID getId() { return id; }
    public UUID getOrganisationId() { return organisationId; }
    /**
     * Whether the licence's promise is being kept on {@code now} (platform ADR-001): broken ({@code LICENSE_OVER_LIMIT}) when more seats are in use than were
     * bought, kept when OK or only near the limit (a notice on the screen, not an incident), not judged when paused or without a count yet.
     */
    public Promise promise(Instant now) {
        return switch (assess(now).level()) {
            case OVER_LIMIT -> Promise.broken("LICENSE_OVER_LIMIT");
            case OK, NEAR_LIMIT -> Promise.kept();
            case PAUSED, NO_DATA -> Promise.notJudged();
        };
    }

    /** The last moment the licence or the facts it is judged on changed: an update, or the latest count measured. */
    public Instant changedAt() {
        return lastMeasuredAt != null && lastMeasuredAt.isAfter(updatedAt) ? lastMeasuredAt : updatedAt;
    }

    public String getProduct() { return product; }
    public Unit getUnit() { return unit; }
    public int getEntitled() { return entitled; }
    public int getWarnPercent() { return warnPercent; }
    public int getReportEveryHours() { return reportEveryHours; }
    public UUID getVendorId() { return vendorId; }
    public UUID getAgreementId() { return agreementId; }
    public LicenseStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Integer getLastInUse() { return lastInUse; }
    public Instant getLastMeasuredAt() { return lastMeasuredAt; }
    public List<LicenseAuditEntry> getAuditTrail() { return Collections.unmodifiableList(auditTrail); }

    // --- Nested Value Objects ---

    public enum LicenseStatus { ACTIVE, PAUSED }

    /** What one seat is: a named user, a device, an installation, a processor core, or something else the contract says. */
    public enum Unit { USER, DEVICE, INSTALL, CORE, OTHER }

    /** PAUSED is not judged; NO_DATA has had no report yet; the rest compare the latest count with the seats bought. */
    public enum Level { PAUSED, NO_DATA, OK, NEAR_LIMIT, OVER_LIMIT }

    public record LicenseAuditEntry(Instant occurredAt, String action, String executor, LicenseStatus fromStatus, LicenseStatus toStatus, String detail) {}

    /** The judgment of one moment: nothing of it is stored. Usage and available seats are null when there is no count (or the licence is paused). */
    public record Assessment(Level level, Double usagePercent, Integer available, boolean stale) {}
}
