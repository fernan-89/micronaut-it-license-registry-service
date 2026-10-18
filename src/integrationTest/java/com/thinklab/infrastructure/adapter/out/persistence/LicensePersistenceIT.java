package com.thinklab.infrastructure.adapter.out.persistence;

import com.mongodb.reactivestreams.client.MongoClient;
import com.thinklab.domain.exception.DuplicateLicenseException;
import com.thinklab.domain.exception.InvalidLicenseStatusException;
import com.thinklab.domain.model.License;
import com.thinklab.domain.model.License.Unit;
import com.thinklab.domain.model.License.LicenseStatus;
import com.thinklab.domain.model.UsageReport;
import com.thinklab.domain.repository.LicenseRepository;
import com.thinklab.domain.repository.UsageReportRepository;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The capacity registry persistence against a real MongoDB: the unique licence product, the unique report key that makes a repeated or simultaneous
 * report the same report, the latest report that only moves forward in time whatever the order of the reports, the trend window read oldest
 * first, the status-guarded save, and the indexes created at startup (the TTL one included).
 */
@MicronautTest(packages = "com.thinklab", transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LicensePersistenceIT implements TestPropertyProvider {

    private static final String DATABASE = "license_registry_it";

    @Override
    public Map<String, String> getProperties() {
        return Map.of("mongodb.uri", MongoContainer.uri(DATABASE));
    }

    @Inject LicenseRepository licenses;
    @Inject UsageReportRepository reports;
    @Inject MongoClient mongoClient;

    private License newLicense(UUID organisation, String name) {
        return License.createNew(UUID.randomUUID(), organisation, name, Unit.USER, 500, 90, 24, UUID.randomUUID(), null, "op-1");
    }

    private UsageReport newReport(UUID organisation, UUID license, int inUse, Instant measuredAt) {
        return UsageReport.createNew(UUID.randomUUID(), organisation, license, inUse, measuredAt, "tool", measuredAt);
    }

    @Test
    @DisplayName("a license is read back whole, only for its own organisation, oldest first; the product is unique per organisation")
    void policyRoundTrip() {
        UUID organisation = UUID.randomUUID();
        License first = licenses.create(newLicense(organisation, "First")).block();
        licenses.create(newLicense(organisation, "Second")).block();

        License found = licenses.findById(first.getId(), organisation).block();

        assertEquals("First", found.getProduct());
        assertEquals(Unit.USER, found.getUnit());
        assertEquals(500, found.getEntitled());
        assertEquals(24, found.getReportEveryHours());
        assertNull(found.getLastInUse());
        assertEquals(1, found.getAuditTrail().size());
        assertNull(licenses.findById(first.getId(), UUID.randomUUID()).block());
        assertEquals(List.of("First", "Second"), licenses.findAll(organisation).map(License::getProduct).collectList().block());
        assertThrows(DuplicateLicenseException.class, () -> licenses.create(newLicense(organisation, "First")).block());
        assertEquals("First", licenses.create(newLicense(UUID.randomUUID(), "First")).block().getProduct());
    }

    @Test
    @DisplayName("a change is guarded by the status loaded: the second writer from the same state loses, and the audit entry is kept; the whole definition is saved")
    void guardedSave() {
        UUID organisation = UUID.randomUUID();
        License created = licenses.create(newLicense(organisation, "Guarded")).block();
        License writerOne = licenses.findById(created.getId(), organisation).block();
        License writerTwo = licenses.findById(created.getId(), organisation).block();

        var entry = writerOne.update("Guarded 2", Unit.DEVICE, 64, 50, 12, null, UUID.randomUUID(), "op-1");
        licenses.save(writerOne, LicenseStatus.ACTIVE, entry).block();
        var other = writerTwo.pause("op-2");

        assertThrows(InvalidLicenseStatusException.class, () -> licenses.save(writerTwo, LicenseStatus.PAUSED, other).block());
        License stored = licenses.findById(created.getId(), organisation).block();
        assertEquals("Guarded 2", stored.getProduct());
        assertEquals(Unit.DEVICE, stored.getUnit());
        assertEquals(64, stored.getEntitled());
        assertEquals(12, stored.getReportEveryHours());
        assertEquals(null, stored.getVendorId());
        assertEquals(LicenseStatus.ACTIVE, stored.getStatus());
        assertEquals(2, stored.getAuditTrail().size());
    }

    @Test
    @DisplayName("a rename to a name already in use is a duplicate")
    void renameDuplicate() {
        UUID organisation = UUID.randomUUID();
        licenses.create(newLicense(organisation, "Taken")).block();
        License other = licenses.create(newLicense(organisation, "Free")).block();
        var entry = other.update("Taken", Unit.USER, 500, 90, 24, null, null, "op-1");

        assertThrows(DuplicateLicenseException.class, () -> licenses.save(other, LicenseStatus.ACTIVE, entry).block());
    }

    @Test
    @DisplayName("the same report reported twice, even at once, is stored once and the others get the stored one back")
    void sameSample() {
        UUID organisation = UUID.randomUUID();
        UUID license = licenses.create(newLicense(organisation, "Reports")).block().getId();
        Instant moment = Instant.now().minusSeconds(3600);

        List<UsageReportRepository.Stored> results = Flux.range(0, 20).flatMap(i -> reports.create(newReport(organisation, license, 10 + i, moment))).collectList().block();

        assertEquals(1, results.stream().filter(UsageReportRepository.Stored::created).count());
        assertEquals(1, results.stream().map(stored -> stored.report().getId()).distinct().count());
        assertEquals(1, reports.findAll(organisation, license, 50).count().block());
        assertTrue(reports.findByNaturalKey(organisation, license, UsageReport.truncate(moment)).block() != null);
        assertNull(reports.findByNaturalKey(organisation, UUID.randomUUID(), UsageReport.truncate(moment)).block());
    }

    @Test
    @DisplayName("reports are listed newest first, by license or for the tenant, tenant-scoped and limited")
    void listing() {
        UUID organisation = UUID.randomUUID();
        UUID license = licenses.create(newLicense(organisation, "Listing")).block().getId();
        UUID otherPolicy = licenses.create(newLicense(organisation, "Other")).block().getId();
        Instant base = Instant.now().minus(Duration.ofDays(1));
        UsageReport old = reports.create(newReport(organisation, license, 1, base)).block().report();
        UsageReport middle = reports.create(newReport(organisation, license, 2, base.plusSeconds(3600))).block().report();
        UsageReport elsewhere = reports.create(newReport(organisation, otherPolicy, 3, base.plusSeconds(7200))).block().report();

        assertEquals(List.of(elsewhere.getId(), middle.getId(), old.getId()), reports.findAll(organisation, null, 50).map(UsageReport::getId).collectList().block());
        assertEquals(List.of(middle.getId(), old.getId()), reports.findAll(organisation, license, 50).map(UsageReport::getId).collectList().block());
        assertEquals(2, reports.findAll(organisation, null, 2).count().block());
        assertEquals(0, reports.findAll(UUID.randomUUID(), null, 50).count().block());
        assertEquals(2, reports.findById(middle.getId(), organisation).block().getInUse());
        assertNull(reports.findById(middle.getId(), UUID.randomUUID()).block());
    }

    @Test
    @DisplayName("the latest report only moves forward: a late report changes nothing")
    void latestMovesForward() {
        UUID organisation = UUID.randomUUID();
        License license = licenses.create(newLicense(organisation, "Latest")).block();
        Instant t1 = Instant.now().minusSeconds(7200);
        Instant t2 = t1.plusSeconds(3600);

        licenses.recordReport(license.getId(), organisation, newReport(organisation, license.getId(), 20, t1)).block();
        assertEquals(20, licenses.findById(license.getId(), organisation).block().getLastInUse());
        licenses.recordReport(license.getId(), organisation, newReport(organisation, license.getId(), 30, t2)).block();
        licenses.recordReport(license.getId(), organisation, newReport(organisation, license.getId(), 5, t1.minusSeconds(600))).block();
        licenses.recordReport(license.getId(), organisation, newReport(organisation, license.getId(), 99, t2)).block();

        License after = licenses.findById(license.getId(), organisation).block();
        assertEquals(30, after.getLastInUse());
        assertEquals(UsageReport.truncate(t2), after.getLastMeasuredAt());
    }

    @Test
    @DisplayName("startup created the unique licence product, the unique report key, the list index and the expiry (TTL) index")
    void indexesExist() {
        List<Document> policyIndexes = Flux.from(mongoClient.getDatabase(DATABASE).getCollection("licenses").listIndexes()).collectList().block();
        List<Document> sampleIndexes = Flux.from(mongoClient.getDatabase(DATABASE).getCollection("usage_reports").listIndexes()).collectList().block();

        assertTrue(policyIndexes.stream().anyMatch(index -> "organisationId_1_product_1".equals(index.getString("name")) && Boolean.TRUE.equals(index.getBoolean("unique"))));
        assertTrue(sampleIndexes.stream().anyMatch(index -> "organisationId_1_licenseId_1_measuredAt_1".equals(index.getString("name")) && Boolean.TRUE.equals(index.getBoolean("unique"))));
        assertTrue(sampleIndexes.stream().anyMatch(index -> "organisationId_1_licenseId_1_measuredAt_-1".equals(index.getString("name"))));
        assertTrue(sampleIndexes.stream().anyMatch(index -> "measuredAt_1".equals(index.getString("name")) && index.get("expireAfterSeconds") != null
                && ((Number) index.get("expireAfterSeconds")).longValue() == 400L * 24 * 3600));
    }
}
