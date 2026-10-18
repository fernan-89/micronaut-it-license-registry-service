package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.MongoTimeoutException;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.micronaut.context.event.StartupEvent;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class LicenseIndexInitializerTest {

    private final MongoClient client = mock(MongoClient.class);

    @Test
    @DisplayName("startup creates the unique license name, the unique report key, the report list index and the expiry (TTL) index")
    void indexes() {
        MongoDatabase database = mock(MongoDatabase.class);
        MongoCollection<Document> licenses = mock(MongoCollection.class);
        MongoCollection<Document> reports = mock(MongoCollection.class);
        when(client.getDatabase("tenant_cap")).thenReturn(database);
        when(database.getCollection("licenses")).thenReturn(licenses);
        when(database.getCollection("usage_reports")).thenReturn(reports);
        when(licenses.createIndex(any(), any(IndexOptions.class))).thenReturn(Mono.just("ok"));
        when(reports.createIndex(any(), any(IndexOptions.class))).thenReturn(Mono.just("ok"));

        new LicenseIndexInitializer(client, "mongodb://mongo:27017/tenant_cap", 400).onApplicationEvent(mock(StartupEvent.class));

        ArgumentCaptor<IndexOptions> policyOptions = ArgumentCaptor.forClass(IndexOptions.class);
        verify(licenses).createIndex(any(), policyOptions.capture());
        assertTrue(policyOptions.getValue().isUnique());
        assertEquals("organisationId_1_product_1", policyOptions.getValue().getName());
        ArgumentCaptor<IndexOptions> sampleOptions = ArgumentCaptor.forClass(IndexOptions.class);
        verify(reports, times(3)).createIndex(any(), sampleOptions.capture());
        assertTrue(sampleOptions.getAllValues().get(0).isUnique());
        assertEquals("organisationId_1_licenseId_1_measuredAt_1", sampleOptions.getAllValues().get(0).getName());
        assertFalse(sampleOptions.getAllValues().get(1).isUnique());
        assertEquals("organisationId_1_licenseId_1_measuredAt_-1", sampleOptions.getAllValues().get(1).getName());
        assertEquals("measuredAt_1", sampleOptions.getAllValues().get(2).getName());
        assertEquals(400L * 24 * 3600, sampleOptions.getAllValues().get(2).getExpireAfter(TimeUnit.SECONDS));
        assertEquals(null, sampleOptions.getAllValues().get(1).getExpireAfter(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("fail-open: an unreachable server or a rejected index is logged, never propagated; the URI may omit the database; arguments are null-checked")
    void failOpen() {
        MongoDatabase database = mock(MongoDatabase.class);
        MongoCollection<Document> collection = mock(MongoCollection.class);
        when(client.getDatabase("thinklab_it_license_registry_db")).thenReturn(database);
        when(database.getCollection(any(String.class))).thenReturn(collection);
        when(collection.createIndex(any(), any(IndexOptions.class))).thenReturn(Mono.error(new MongoTimeoutException("no server")))
                .thenReturn(Mono.error(new IllegalStateException("rejected"))).thenReturn(Mono.just("ok"));
        var initializer = new LicenseIndexInitializer(client, "mongodb://mongo:27017", 400);

        initializer.onApplicationEvent(mock(StartupEvent.class));

        verify(collection, times(4)).createIndex(any(), any(IndexOptions.class));
        assertThrows(NullPointerException.class, () -> initializer.onApplicationEvent(null));
        assertThrows(NullPointerException.class, () -> new LicenseIndexInitializer(null, "mongodb://mongo:27017", 400));
        assertThrows(NullPointerException.class, () -> new LicenseIndexInitializer(client, null, 400));
    }
}
