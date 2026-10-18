package com.thinklab.application.mapper;

import com.thinklab.application.dto.response.AuditEntryResponse;
import com.thinklab.application.dto.response.LicenseResponse;
import com.thinklab.application.dto.response.UsageReportResponse;
import com.thinklab.domain.model.License;
import com.thinklab.domain.model.License.Unit;
import com.thinklab.domain.model.UsageReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LicenseMapperTest {

    private static final Instant NOW = Instant.parse("2026-10-18T12:00:00Z");

    @Test
    @DisplayName("the mapper is a utility class")
    void utility() throws ReflectiveOperationException {
        Constructor<LicenseMapper> constructor = LicenseMapper.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThrows(InvocationTargetException.class, constructor::newInstance);
    }

    @Test
    @DisplayName("a licence answers what was bought, the latest count and how it was judged")
    void license() {
        UUID vendor = UUID.randomUUID();
        UUID agreement = UUID.randomUUID();
        License license = License.reconstitute(UUID.randomUUID(), UUID.randomUUID(), "Office", Unit.USER, 100, 90, 24, vendor, agreement, License.LicenseStatus.ACTIVE,
                NOW, NOW, 60, NOW, List.of());

        LicenseResponse response = LicenseMapper.toResponse(license, license.assess(NOW));

        assertEquals("Office", response.product());
        assertEquals("USER", response.unit());
        assertEquals(100, response.entitled());
        assertEquals(90, response.warnPercent());
        assertEquals(24, response.reportEveryHours());
        assertEquals(vendor, response.vendorId());
        assertEquals(agreement, response.agreementId());
        assertEquals("ACTIVE", response.status());
        assertEquals("OK", response.level());
        assertEquals(60.0, response.usagePercent());
        assertEquals(40, response.available());
        assertEquals(60, response.lastInUse());
        assertEquals(NOW, response.lastMeasuredAt());
    }

    @Test
    @DisplayName("a paused licence answers no usage at all")
    void pausedLicense() {
        License license = License.reconstitute(UUID.randomUUID(), UUID.randomUUID(), "n", Unit.CORE, 8, 60, 6, null, null, License.LicenseStatus.PAUSED, NOW, NOW, null, null,
                List.of());

        LicenseResponse response = LicenseMapper.toResponse(license, license.assess(NOW));

        assertEquals("PAUSED", response.level());
        assertNull(response.usagePercent());
        assertNull(response.available());
        assertNull(response.lastInUse());
    }

    @Test
    @DisplayName("a report answers what was measured and who reported it")
    void report() {
        UsageReport report = UsageReport.createNew(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 12, NOW, "tool", NOW);

        UsageReportResponse response = LicenseMapper.toResponse(report);

        assertEquals(12, response.inUse());
        assertEquals(NOW, response.measuredAt());
        assertEquals("tool", response.reportedBy());
        assertEquals(report.getLicenseId(), response.licenseId());
    }

    @Test
    @DisplayName("an audit entry keeps the previous status when there is one")
    void audit() {
        License license = License.createNew(UUID.randomUUID(), UUID.randomUUID(), "n", Unit.CORE, 8, 60, 6, null, null, "op");
        license.pause("op");

        List<AuditEntryResponse> trail = license.getAuditTrail().stream().map(LicenseMapper::toResponse).toList();

        assertNull(trail.get(0).fromStatus());
        assertEquals("ACTIVE", trail.get(1).fromStatus());
        assertEquals("PAUSED", trail.get(1).toStatus());
    }
}
