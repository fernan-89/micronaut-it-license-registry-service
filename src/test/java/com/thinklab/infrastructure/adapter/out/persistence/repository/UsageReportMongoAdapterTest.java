package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.MongoWriteException;
import com.mongodb.ServerAddress;
import com.mongodb.WriteError;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.thinklab.domain.model.UsageReport;
import com.thinklab.infrastructure.adapter.out.persistence.entity.UsageReportDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.UsageReportDocument.UsageReportPersistenceMapper;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class UsageReportMongoAdapterTest {

    private static final String URI = "mongodb://localhost:27017/cap_test";

    private final UUID org = UUID.randomUUID();
    private MongoClient client;
    private MongoCollection<UsageReportDocument> collection;

    @BeforeEach
    void setUp() {
        client = mock(MongoClient.class);
        MongoDatabase database = mock(MongoDatabase.class);
        collection = mock(MongoCollection.class);
        when(client.getDatabase("cap_test")).thenReturn(database);
        when(client.getDatabase("thinklab_it_license_registry_db")).thenReturn(database);
        when(database.getCollection("usage_reports", UsageReportDocument.class)).thenReturn(collection);
        when(collection.withCodecRegistry(any())).thenReturn(collection);
    }

    private UsageReport report(int inUse, Instant measuredAt) {
        return UsageReport.createNew(UUID.randomUUID(), org, UUID.randomUUID(), inUse, measuredAt, "tool", measuredAt);
    }

    private static MongoWriteException writeError(int code) {
        return new MongoWriteException(new WriteError(code, "write error", new BsonDocument()), new ServerAddress());
    }

    private FindPublisher<UsageReportDocument> finds(UsageReport... found) {
        FindPublisher<UsageReportDocument> publisher = mock(FindPublisher.class);
        when(collection.find(any(Bson.class))).thenReturn(publisher);
        when(publisher.first()).thenReturn(publisher);
        when(publisher.sort(any(Bson.class))).thenReturn(publisher);
        when(publisher.limit(anyInt())).thenReturn(publisher);
        doAnswer(invocation -> {
            org.reactivestreams.Subscriber<UsageReportDocument> subscriber = invocation.getArgument(0);
            Flux.fromArray(found).map(UsageReportPersistenceMapper::toDocument).subscribe(subscriber);
            return null;
        }).when(publisher).subscribe(any());
        return publisher;
    }

    @Test
    @DisplayName("a report survives the round trip, with its count, its moment and who reported it")
    void roundTrip() {
        UsageReport report = report(42, Instant.now());
        UsageReportDocument document = UsageReportPersistenceMapper.toDocument(report);
        UsageReport back = UsageReportPersistenceMapper.toDomain(document);

        assertEquals(42, back.getInUse());
        assertEquals(report.getMeasuredAt(), back.getMeasuredAt());
        assertEquals("tool", back.getReportedBy());
        assertEquals(report.getReportedAt(), back.getReportedAt());
        assertEquals(org, document.getOrganisationId());
        assertEquals(report.getLicenseId(), document.getLicenseId());
        assertEquals(report.getId(), document.getId());
        assertEquals(42, document.getInUse());
    }

    @Test
    @DisplayName("the persistence mapper is a utility class")
    void mapperIsUtility() throws ReflectiveOperationException {
        Constructor<UsageReportPersistenceMapper> constructor = UsageReportPersistenceMapper.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThrows(InvocationTargetException.class, constructor::newInstance);
    }

    @Test
    @DisplayName("create answers the report as created; the lookups are scoped to the organisation; the URI may omit the database and must not be null")
    void lookups() {
        UsageReport report = report(1, Instant.now());
        when(collection.insertOne(any(UsageReportDocument.class))).thenReturn(Mono.just(InsertOneResult.acknowledged(new BsonObjectId(new ObjectId()))));
        finds(report);
        UsageReportMongoRepositoryAdapter adapter = new UsageReportMongoRepositoryAdapter(client, "mongodb://localhost:27017");

        StepVerifier.create(adapter.create(report)).assertNext(stored -> assertTrue(stored.created())).verifyComplete();
        StepVerifier.create(adapter.findById(report.getId(), org)).assertNext(found -> assertEquals(report.getId(), found.getId())).verifyComplete();
        StepVerifier.create(adapter.findByNaturalKey(org, report.getLicenseId(), report.getMeasuredAt())).expectNextCount(1).verifyComplete();
        assertThrows(NullPointerException.class, () -> new UsageReportMongoRepositoryAdapter(client, null));
    }

    @Test
    @DisplayName("a duplicate key means the report was already reported: the stored one comes back, not created; any other error passes through")
    void duplicate() {
        UsageReport report = report(1, Instant.now());
        when(collection.insertOne(any(UsageReportDocument.class))).thenReturn(Mono.error(writeError(11000))).thenReturn(Mono.error(writeError(1)));
        finds(report);
        UsageReportMongoRepositoryAdapter adapter = new UsageReportMongoRepositoryAdapter(client, URI);

        StepVerifier.create(adapter.create(report)).assertNext(stored -> {
            assertFalse(stored.created());
            assertEquals(report.getId(), stored.report().getId());
        }).verifyComplete();
        StepVerifier.create(adapter.create(report)).expectError(MongoWriteException.class).verify();
    }

    @Test
    @DisplayName("the list is scoped to the organisation, to the license when one is given, newest first and limited")
    void list() {
        UsageReport report = report(1, Instant.now());
        FindPublisher<UsageReportDocument> publisher = finds(report);
        UsageReportMongoRepositoryAdapter adapter = new UsageReportMongoRepositoryAdapter(client, URI);

        StepVerifier.create(adapter.findAll(org, null, 5)).expectNextCount(1).verifyComplete();
        StepVerifier.create(adapter.findAll(org, report.getLicenseId(), 7)).expectNextCount(1).verifyComplete();

        ArgumentCaptor<Bson> filter = ArgumentCaptor.forClass(Bson.class);
        verify(collection, times(2)).find(filter.capture());
        assertTrue(filter.getAllValues().get(0).toString().contains("organisationId") && !filter.getAllValues().get(0).toString().contains("licenseId"));
        assertTrue(filter.getAllValues().get(1).toString().contains("licenseId"));
        verify(publisher).limit(5);
        verify(publisher).limit(7);
    }
}
