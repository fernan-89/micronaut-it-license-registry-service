package com.thinklab.domain.model;

import com.thinklab.domain.exception.InvalidLicenseStatusException;
import com.thinklab.domain.model.License.Assessment;
import com.thinklab.domain.model.License.Level;
import com.thinklab.domain.model.License.LicenseAuditEntry;
import com.thinklab.domain.model.License.LicenseStatus;
import com.thinklab.domain.model.License.Unit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LicenseTest {

    private static final Instant NOW = Instant.parse("2026-10-18T12:00:00Z");

    private final UUID org = UUID.randomUUID();
    private final UUID vendor = UUID.randomUUID();
    private final UUID agreement = UUID.randomUUID();

    private License created() {
        return License.createNew(UUID.randomUUID(), org, "Office 365 E3", Unit.USER, 100, 90, 24, vendor, agreement, "op");
    }

    /** 100 seats, warn at 90 percent, a report expected every 24 hours, with the latest count given. */
    private License stored(LicenseStatus status, Integer inUse, Instant measuredAt) {
        return License.reconstitute(UUID.randomUUID(), org, "Office 365 E3", Unit.USER, 100, 90, 24, null, null, status, NOW.minus(Duration.ofDays(40)),
                NOW.minus(Duration.ofDays(40)), inUse, measuredAt, List.of());
    }

    private static void rejects(Executable action) {
        assertThrows(IllegalArgumentException.class, action);
    }

    @Test
    @DisplayName("a new licence is ACTIVE, keeps what it was given, has no count yet and starts its trail")
    void createNew() {
        License license = created();

        assertEquals(LicenseStatus.ACTIVE, license.getStatus());
        assertEquals("Office 365 E3", license.getProduct());
        assertEquals(Unit.USER, license.getUnit());
        assertEquals(100, license.getEntitled());
        assertEquals(90, license.getWarnPercent());
        assertEquals(24, license.getReportEveryHours());
        assertEquals(vendor, license.getVendorId());
        assertEquals(agreement, license.getAgreementId());
        assertNull(license.getLastInUse());
        assertNull(license.getLastMeasuredAt());
        assertEquals("100 USER seats of Office 365 E3.", license.getAuditTrail().get(0).detail());
        assertNull(license.getAuditTrail().get(0).fromStatus());
    }

    @Test
    @DisplayName("a licence needs an identity, a product of up to 80 characters, a unit, 1 to 10 million seats, a warning of 1 to 100 percent, 1 to 720 hours and an executor")
    void validation() {
        UUID id = UUID.randomUUID();
        rejects(() -> License.createNew(null, org, "P", Unit.USER, 1, 90, 24, null, null, "op"));
        rejects(() -> License.createNew(id, null, "P", Unit.USER, 1, 90, 24, null, null, "op"));
        rejects(() -> License.createNew(id, org, null, Unit.USER, 1, 90, 24, null, null, "op"));
        rejects(() -> License.createNew(id, org, " ", Unit.USER, 1, 90, 24, null, null, "op"));
        rejects(() -> License.createNew(id, org, "x".repeat(81), Unit.USER, 1, 90, 24, null, null, "op"));
        rejects(() -> License.createNew(id, org, "P", null, 1, 90, 24, null, null, "op"));
        rejects(() -> License.createNew(id, org, "P", Unit.USER, 0, 90, 24, null, null, "op"));
        rejects(() -> License.createNew(id, org, "P", Unit.USER, 10_000_001, 90, 24, null, null, "op"));
        rejects(() -> License.createNew(id, org, "P", Unit.USER, 1, 0, 24, null, null, "op"));
        rejects(() -> License.createNew(id, org, "P", Unit.USER, 1, 101, 24, null, null, "op"));
        rejects(() -> License.createNew(id, org, "P", Unit.USER, 1, 90, 0, null, null, "op"));
        rejects(() -> License.createNew(id, org, "P", Unit.USER, 1, 90, 721, null, null, "op"));
        rejects(() -> License.createNew(id, org, "P", Unit.USER, 1, 90, 24, null, null, null));
        rejects(() -> License.createNew(id, org, "P", Unit.USER, 1, 90, 24, null, null, " "));
        assertEquals(10_000_000, License.createNew(id, org, "x".repeat(80), Unit.CORE, 10_000_000, 100, 720, null, null, "op").getEntitled());
        assertEquals(1, License.createNew(id, org, "P", Unit.OTHER, 1, 1, 1, null, null, "op").getEntitled());
    }

    @Test
    @DisplayName("update changes the whole definition in any status and records it; the latest count is not touched")
    void update() {
        License license = stored(LicenseStatus.ACTIVE, 50, NOW);

        LicenseAuditEntry entry = license.update("Office 365 E5", Unit.DEVICE, 200, 80, 12, vendor, null, "op-2");

        assertEquals("Office 365 E5", license.getProduct());
        assertEquals(Unit.DEVICE, license.getUnit());
        assertEquals(200, license.getEntitled());
        assertEquals(80, license.getWarnPercent());
        assertEquals(12, license.getReportEveryHours());
        assertEquals(vendor, license.getVendorId());
        assertNull(license.getAgreementId());
        assertEquals(50, license.getLastInUse());
        assertEquals("UPDATED", entry.action());
        assertEquals("Licence updated: 200 DEVICE seats.", entry.detail());
        license.pause("op");
        license.update("P", Unit.USER, 1, 90, 24, null, null, "op");
        assertEquals(LicenseStatus.PAUSED, license.getStatus());
        rejects(() -> license.update("P", Unit.USER, 0, 90, 24, null, null, "op"));
        rejects(() -> license.update("P", Unit.USER, 1, 90, 24, null, null, null));
    }

    @Test
    @DisplayName("pause and resume go one way each; the wrong state is a conflict")
    void lifecycle() {
        License license = created();

        assertThrows(InvalidLicenseStatusException.class, () -> license.resume("op"));
        assertEquals("PAUSED", license.pause("op").action());
        assertThrows(InvalidLicenseStatusException.class, () -> license.pause("op"));
        assertEquals(LicenseStatus.PAUSED, license.resume("op").fromStatus());
        assertEquals(LicenseStatus.ACTIVE, license.getStatus());
        rejects(() -> license.pause(null));
        license.pause("op");
        rejects(() -> license.resume(" "));
    }

    @Test
    @DisplayName("a licence is judged on its latest count: OK below the warning, NEAR_LIMIT from it (the limit itself included), OVER_LIMIT above the seats bought")
    void assess() {
        assertEquals(new Assessment(Level.OK, 89.0, 11, false), stored(LicenseStatus.ACTIVE, 89, NOW).assess(NOW));
        assertEquals(new Assessment(Level.NEAR_LIMIT, 90.0, 10, false), stored(LicenseStatus.ACTIVE, 90, NOW).assess(NOW));
        assertEquals(new Assessment(Level.NEAR_LIMIT, 100.0, 0, false), stored(LicenseStatus.ACTIVE, 100, NOW).assess(NOW));
        assertEquals(new Assessment(Level.OVER_LIMIT, 105.0, -5, false), stored(LicenseStatus.ACTIVE, 105, NOW).assess(NOW));
        assertEquals(new Assessment(Level.OK, 0.0, 100, false), stored(LicenseStatus.ACTIVE, 0, NOW).assess(NOW));
    }

    @Test
    @DisplayName("no count yet is NO_DATA, a paused licence is not judged, and a count older than twice the interval is stale but still judged")
    void assessEdges() {
        assertEquals(new Assessment(Level.NO_DATA, null, null, false), stored(LicenseStatus.ACTIVE, null, null).assess(NOW));
        assertEquals(new Assessment(Level.PAUSED, null, null, false), stored(LicenseStatus.PAUSED, 120, NOW).assess(NOW));
        assertTrue(stored(LicenseStatus.ACTIVE, 120, NOW.minus(Duration.ofHours(49))).assess(NOW).stale());
        assertEquals(Level.OVER_LIMIT, stored(LicenseStatus.ACTIVE, 120, NOW.minus(Duration.ofHours(49))).assess(NOW).level());
        assertEquals(false, stored(LicenseStatus.ACTIVE, 120, NOW.minus(Duration.ofHours(48))).assess(NOW).stale());
    }

    @Test
    @DisplayName("reconstitute keeps what was stored, defaults what is missing, and refuses a missing identity")
    void reconstitute() {
        UUID id = UUID.randomUUID();
        License full = License.reconstitute(id, org, "P", Unit.INSTALL, 5, 90, 24, vendor, agreement, LicenseStatus.PAUSED, NOW, NOW.plusSeconds(5), 3, NOW,
                List.of(new LicenseAuditEntry(NOW, "INITIATED", "op", null, LicenseStatus.ACTIVE, "d")));
        assertEquals(LicenseStatus.PAUSED, full.getStatus());
        assertEquals(NOW.plusSeconds(5), full.getUpdatedAt());
        assertEquals(3, full.getLastInUse());
        assertEquals(1, full.getAuditTrail().size());

        License bare = License.reconstitute(id, org, "P", Unit.INSTALL, 5, 90, 24, null, null, null, null, null, null, null, null);
        assertEquals(LicenseStatus.ACTIVE, bare.getStatus());
        assertTrue(bare.getAuditTrail().isEmpty());
        assertEquals(bare.getCreatedAt(), bare.getUpdatedAt());
        rejects(() -> License.reconstitute(null, org, "P", Unit.INSTALL, 5, 90, 24, null, null, null, null, null, null, null, null));
        rejects(() -> License.reconstitute(id, null, "P", Unit.INSTALL, 5, 90, 24, null, null, null, null, null, null, null, null));
        rejects(() -> License.reconstitute(id, org, null, Unit.INSTALL, 5, 90, 24, null, null, null, null, null, null, null, null));
        rejects(() -> License.reconstitute(id, org, "P", null, 5, 90, 24, null, null, null, null, null, null, null, null));
        assertThrows(UnsupportedOperationException.class, () -> full.getAuditTrail().clear());
    }

    @Test
    @DisplayName("the promise is broken (LICENSE_OVER_LIMIT) over the limit, kept when OK or near it, and not judged when paused or without a count (platform ADR-001)")
    void promise() {
        assertEquals(Promise.broken("LICENSE_OVER_LIMIT"), stored(LicenseStatus.ACTIVE, 120, NOW).promise(NOW));
        assertEquals(Promise.kept(), stored(LicenseStatus.ACTIVE, 50, NOW).promise(NOW));
        assertEquals(Promise.kept(), stored(LicenseStatus.ACTIVE, 95, NOW).promise(NOW));
        assertEquals(Promise.notJudged(), stored(LicenseStatus.ACTIVE, null, null).promise(NOW));
        assertEquals(Promise.notJudged(), stored(LicenseStatus.PAUSED, 120, NOW).promise(NOW));
        assertEquals(Promise.Verdict.BROKEN, Promise.broken("X").verdict());
        assertEquals("X", Promise.broken("X").code());
    }

    @Test
    @DisplayName("a licence changed when it was updated or when its latest count was measured, whichever is later")
    void changedAt() {
        License counted = stored(LicenseStatus.ACTIVE, 50, NOW.minus(Duration.ofHours(2)));
        License noCount = stored(LicenseStatus.ACTIVE, null, null);
        License updatedLater = License.reconstitute(UUID.randomUUID(), org, "P", Unit.USER, 100, 90, 24, null, null, LicenseStatus.ACTIVE, NOW.minus(Duration.ofDays(40)),
                NOW.minus(Duration.ofHours(1)), 50, NOW.minus(Duration.ofDays(3)), List.of());

        assertEquals(NOW.minus(Duration.ofHours(2)), counted.changedAt());
        assertEquals(NOW.minus(Duration.ofDays(40)), noCount.changedAt());
        assertEquals(NOW.minus(Duration.ofHours(1)), updatedLater.changedAt());
    }
}
