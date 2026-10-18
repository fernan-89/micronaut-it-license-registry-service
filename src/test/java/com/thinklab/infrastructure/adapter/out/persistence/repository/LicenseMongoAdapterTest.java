package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.MongoWriteException;
import com.mongodb.ServerAddress;
import com.mongodb.WriteError;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.thinklab.domain.exception.DuplicateLicenseException;
import com.thinklab.domain.exception.InvalidLicenseStatusException;
import com.thinklab.domain.model.License;
import com.thinklab.domain.model.License.Unit;
import com.thinklab.domain.model.License.LicenseStatus;
import com.thinklab.domain.model.UsageReport;
import com.thinklab.infrastructure.adapter.out.persistence.entity.LicenseDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.LicenseDocument.LicensePersistenceMapper;
import org.bson.BsonDocument;
import org.bson.BsonObjectId;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class LicenseMongoAdapterTest {

    private static final String URI = "mongodb://localhost:27017/cap_test";

    private final UUID org = UUID.randomUUID();
    private MongoClient client;
    private MongoCollection<LicenseDocument> collection;

    @BeforeEach
    void setUp() {
        client = mock(MongoClient.class);
        MongoDatabase database = mock(MongoDatabase.class);
        collection = mock(MongoCollection.class);
        when(client.getDatabase("cap_test")).thenReturn(database);
        when(client.getDatabase("thinklab_it_license_registry_db")).thenReturn(database);
        when(database.getCollection("licenses", LicenseDocument.class)).thenReturn(collection);
        when(collection.withCodecRegistry(any())).thenReturn(collection);
    }

    private License license() {
        License license = License.createNew(UUID.randomUUID(), org, "Office", Unit.USER, 100, 90, 24, UUID.randomUUID(), null, "op");
        license.pause("op");
        return license;
    }

    private UsageReport report(Instant measuredAt) {
        return UsageReport.createNew(UUID.randomUUID(), org, UUID.randomUUID(), 61, measuredAt, "tool", measuredAt);
    }

    private static MongoWriteException writeError(int code) {
        return new MongoWriteException(new WriteError(code, "write error", new BsonDocument()), new ServerAddress());
    }

    private void finds(License found) {
        FindPublisher<LicenseDocument> publisher = mock(FindPublisher.class);
        when(collection.find(any(Bson.class))).thenReturn(publisher);
        when(publisher.first()).thenReturn(publisher);
        when(publisher.sort(any(Bson.class))).thenReturn(publisher);
        doAnswer(invocation -> {
            org.reactivestreams.Subscriber<LicenseDocument> subscriber = invocation.getArgument(0);
            Flux.just(LicensePersistenceMapper.toDocument(found)).subscribe(subscriber);
            return null;
        }).when(publisher).subscribe(any());
    }

    @Test
    @DisplayName("a licence survives the round trip, with what was bought, its latest count and its audit trail")
    void roundTrip() {
        License license = license();
        Instant at = Instant.parse("2026-10-18T10:00:00Z");
        UUID agreement = UUID.randomUUID();
        License counted = License.reconstitute(license.getId(), org, license.getProduct(), Unit.DEVICE, 250, 80, 12, license.getVendorId(), agreement, LicenseStatus.PAUSED,
                at, at, 33, at, license.getAuditTrail());

        LicenseDocument document = LicensePersistenceMapper.toDocument(counted);
        License back = LicensePersistenceMapper.toDomain(document);

        assertEquals(LicenseStatus.PAUSED, back.getStatus());
        assertEquals(Unit.DEVICE, back.getUnit());
        assertEquals(250, back.getEntitled());
        assertEquals(80, back.getWarnPercent());
        assertEquals(12, back.getReportEveryHours());
        assertEquals(license.getVendorId(), back.getVendorId());
        assertEquals(agreement, back.getAgreementId());
        assertEquals(33, back.getLastInUse());
        assertEquals(at, back.getLastMeasuredAt());
        assertEquals(2, back.getAuditTrail().size());
        assertNull(back.getAuditTrail().get(0).fromStatus());
        assertEquals(LicenseStatus.ACTIVE, back.getAuditTrail().get(1).fromStatus());
        assertEquals("Office", document.getProduct());
        assertEquals("PAUSED", document.getStatus());
        assertEquals("DEVICE", document.getUnit());
        assertEquals(org, document.getOrganisationId());
        assertEquals(license.getId(), document.getId());
        assertEquals(at, document.getCreatedAt());
        assertEquals(at, document.getUpdatedAt());
        assertEquals(2, document.getAuditTrail().size());
    }

    @Test
    @DisplayName("the document setters are plain, and the persistence mapper is a utility class")
    void plainDocument() throws ReflectiveOperationException {
        LicenseDocument document = new LicenseDocument();
        document.setLastInUse(null);
        document.setLastMeasuredAt(null);
        document.setAuditTrail(new ArrayList<>());
        assertNull(document.getLastInUse());
        assertNull(document.getLastMeasuredAt());
        assertTrue(document.getAuditTrail().isEmpty());

        Constructor<LicensePersistenceMapper> constructor = LicensePersistenceMapper.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThrows(InvocationTargetException.class, constructor::newInstance);
    }

    @Test
    @DisplayName("create and the lookups are scoped to the organisation, the list oldest first")
    void lookups() {
        License license = license();
        when(collection.insertOne(any(LicenseDocument.class))).thenReturn(Mono.just(InsertOneResult.acknowledged(new BsonObjectId(new ObjectId()))));
        finds(license);
        LicenseMongoRepositoryAdapter adapter = new LicenseMongoRepositoryAdapter(client, URI);

        StepVerifier.create(adapter.create(license)).expectNext(license).verifyComplete();
        StepVerifier.create(adapter.findById(license.getId(), org)).assertNext(found -> assertEquals(license.getId(), found.getId())).verifyComplete();
        StepVerifier.create(adapter.findAll(org)).expectNextCount(1).verifyComplete();

        ArgumentCaptor<Bson> filter = ArgumentCaptor.forClass(Bson.class);
        verify(collection, times(2)).find(filter.capture());
        assertTrue(filter.getAllValues().get(0).toString().contains("organisationId"));
    }

    @Test
    @DisplayName("a duplicate name is a 409 of the domain, on create or on a rename; any other write error passes through; the URI may omit the database")
    void duplicates() {
        License license = license();
        when(collection.insertOne(any(LicenseDocument.class))).thenReturn(Mono.error(writeError(11000))).thenReturn(Mono.error(writeError(1)));
        when(collection.updateOne(any(Bson.class), any(Bson.class))).thenReturn(Mono.error(writeError(11000))).thenReturn(Mono.error(writeError(2)));
        LicenseMongoRepositoryAdapter adapter = new LicenseMongoRepositoryAdapter(client, "mongodb://localhost:27017");

        StepVerifier.create(adapter.create(license)).expectError(DuplicateLicenseException.class).verify();
        StepVerifier.create(adapter.create(license)).expectError(MongoWriteException.class).verify();
        StepVerifier.create(adapter.save(license, LicenseStatus.ACTIVE, license.getAuditTrail().get(1))).expectError(DuplicateLicenseException.class).verify();
        StepVerifier.create(adapter.save(license, LicenseStatus.ACTIVE, license.getAuditTrail().get(1))).expectError(MongoWriteException.class).verify();
        assertThrows(NullPointerException.class, () -> new LicenseMongoRepositoryAdapter(client, null));
    }

    @Test
    @DisplayName("save is guarded by the status loaded and pushes the audit entry; a lost race is a conflict")
    void save() {
        License license = license();
        when(collection.updateOne(any(Bson.class), any(Bson.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(0, 0L, null)));
        LicenseMongoRepositoryAdapter adapter = new LicenseMongoRepositoryAdapter(client, URI);

        StepVerifier.create(adapter.save(license, LicenseStatus.ACTIVE, license.getAuditTrail().get(1))).verifyComplete();
        StepVerifier.create(adapter.save(license, LicenseStatus.ACTIVE, license.getAuditTrail().get(1))).expectError(InvalidLicenseStatusException.class).verify();

        ArgumentCaptor<Bson> guard = ArgumentCaptor.forClass(Bson.class);
        ArgumentCaptor<Bson> update = ArgumentCaptor.forClass(Bson.class);
        verify(collection, times(2)).updateOne(guard.capture(), update.capture());
        assertTrue(guard.getAllValues().get(0).toString().contains("ACTIVE") && guard.getAllValues().get(0).toString().contains("organisationId"));
        assertTrue(update.getAllValues().get(0).toString().contains("auditTrail"));
    }

    @Test
    @DisplayName("save writes the whole definition next to the status and the audit entry")
    void saveWritesTheDefinition() {
        License license = license();
        when(collection.updateOne(any(Bson.class), any(Bson.class))).thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));
        LicenseMongoRepositoryAdapter adapter = new LicenseMongoRepositoryAdapter(client, URI);

        StepVerifier.create(adapter.save(license, LicenseStatus.ACTIVE, license.getAuditTrail().get(1))).verifyComplete();

        ArgumentCaptor<Bson> update = ArgumentCaptor.forClass(Bson.class);
        verify(collection).updateOne(any(Bson.class), update.capture());
        String text = update.getValue().toString();
        for (String field : new String[]{"product", "unit", "entitled", "warnPercent", "reportEveryHours", "vendorId", "agreementId"}) {
            assertTrue(text.contains(field), field);
        }
        assertFalse(text.contains("lastInUse"));
    }

    @Test
    @DisplayName("a report moves the latest one forward only when it is newer than the one kept (or none is kept)")
    void recordReport() {
        when(collection.updateOne(any(Bson.class), any(Bson.class))).thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));
        LicenseMongoRepositoryAdapter adapter = new LicenseMongoRepositoryAdapter(client, URI);
        UUID licenseId = UUID.randomUUID();
        UsageReport report = report(Instant.parse("2026-10-18T10:00:00Z"));

        StepVerifier.create(adapter.recordReport(licenseId, org, report)).verifyComplete();

        ArgumentCaptor<Bson> filter = ArgumentCaptor.forClass(Bson.class);
        ArgumentCaptor<Bson> update = ArgumentCaptor.forClass(Bson.class);
        verify(collection).updateOne(filter.capture(), update.capture());
        String guard = filter.getValue().toString();
        assertTrue(guard.contains("organisationId") && guard.contains("lastMeasuredAt") && guard.contains("$lt"));
        String set = update.getValue().toString();
        assertTrue(set.contains("lastMeasuredAt") && set.contains("lastInUse") && set.contains("61"));
    }
}
