package com.thinklab.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UsageReportTest {

    private final UUID id = UUID.randomUUID();
    private final UUID org = UUID.randomUUID();
    private final UUID license = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-18T12:00:00Z");
    private final Instant measured = Instant.parse("2026-10-18T10:00:00.123456Z");

    private static void rejects(Executable action) {
        assertThrows(IllegalArgumentException.class, action);
    }

    @Test
    @DisplayName("a report is a fact: the moment is cut to the millisecond, who reported it and when are kept")
    void createNew() {
        UsageReport report = UsageReport.createNew(id, org, license, 42, measured, "tool", now);

        assertEquals(id, report.getId());
        assertEquals(org, report.getOrganisationId());
        assertEquals(license, report.getLicenseId());
        assertEquals(42, report.getInUse());
        assertEquals(Instant.parse("2026-10-18T10:00:00.123Z"), report.getMeasuredAt());
        assertEquals("tool", report.getReportedBy());
        assertEquals(now, report.getReportedAt());
        assertEquals(0, UsageReport.createNew(id, org, license, 0, measured, "tool", now).getInUse());
    }

    @Test
    @DisplayName("identity, a count of zero or more, the moment and the reporter are checked; a few minutes of clock skew are allowed, more is the future")
    void validation() {
        rejects(() -> UsageReport.createNew(null, org, license, 1, measured, "tool", now));
        rejects(() -> UsageReport.createNew(id, null, license, 1, measured, "tool", now));
        rejects(() -> UsageReport.createNew(id, org, null, 1, measured, "tool", now));
        rejects(() -> UsageReport.createNew(id, org, license, -1, measured, "tool", now));
        rejects(() -> UsageReport.createNew(id, org, license, 1, null, "tool", now));
        rejects(() -> UsageReport.createNew(id, org, license, 1, measured, null, now));
        rejects(() -> UsageReport.createNew(id, org, license, 1, measured, " ", now));
        UsageReport skewed = UsageReport.createNew(id, org, license, 1, now.plus(UsageReport.CLOCK_SKEW), "tool", now);
        assertEquals(now.plus(UsageReport.CLOCK_SKEW), skewed.getMeasuredAt());
        rejects(() -> UsageReport.createNew(id, org, license, 1, now.plus(UsageReport.CLOCK_SKEW).plus(Duration.ofMillis(1)), "tool", now));
    }

    @Test
    @DisplayName("reconstitute keeps what was stored and refuses a missing identity or moment")
    void reconstitute() {
        UsageReport report = UsageReport.reconstitute(id, org, license, 7, measured, "tool", now);

        assertEquals(measured, report.getMeasuredAt());
        assertEquals(7, report.getInUse());
        rejects(() -> UsageReport.reconstitute(null, org, license, 7, measured, "tool", now));
        rejects(() -> UsageReport.reconstitute(id, null, license, 7, measured, "tool", now));
        rejects(() -> UsageReport.reconstitute(id, org, null, 7, measured, "tool", now));
        rejects(() -> UsageReport.reconstitute(id, org, license, 7, null, "tool", now));
    }
}
