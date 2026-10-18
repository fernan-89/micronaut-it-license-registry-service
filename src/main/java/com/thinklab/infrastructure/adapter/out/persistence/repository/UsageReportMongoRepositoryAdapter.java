package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.thinklab.domain.model.UsageReport;
import com.thinklab.domain.repository.UsageReportRepository;
import com.thinklab.infrastructure.adapter.out.persistence.entity.UsageReportDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.UsageReportDocument.UsageReportPersistenceMapper;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Singleton;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * MongoDB Reactive Repository Adapter for the UsageReport aggregate, raw reactive-streams driver. Reports are only ever inserted. The unique
 * {@code (organisationId, licenseId, measuredAt)} index is what makes a repeated report the same report: the loser of an insert reads the
 * stored one (ADR-030).
 */
@Singleton
public class UsageReportMongoRepositoryAdapter implements UsageReportRepository {

    static final String COLLECTION_NAME = "usage_reports";
    private static final String FIELD_ID = "_id";
    private static final String FIELD_ORGANISATION = "organisationId";
    private static final String FIELD_LICENSE = "licenseId";
    private static final String FIELD_MEASURED_AT = "measuredAt";

    private static final CodecRegistry POJO_CODEC_REGISTRY = CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build())
    );

    private final MongoClient mongoClient;
    private final String database;

    public UsageReportMongoRepositoryAdapter(MongoClient mongoClient, @Property(name = "mongodb.uri") String mongoUri) {
        this.mongoClient = mongoClient;
        String configured = new ConnectionString(Objects.requireNonNull(mongoUri, "mongodb.uri cannot be null.")).getDatabase();
        this.database = configured != null ? configured : LicenseMongoRepositoryAdapter.DEFAULT_DATABASE;
    }

    private MongoCollection<UsageReportDocument> getCollection() {
        return mongoClient.getDatabase(database).getCollection(COLLECTION_NAME, UsageReportDocument.class).withCodecRegistry(POJO_CODEC_REGISTRY);
    }

    @Override
    public Mono<Stored> create(UsageReport report) {
        return Mono.from(getCollection().insertOne(UsageReportPersistenceMapper.toDocument(report)))
                .map(result -> new Stored(report, true))
                .onErrorResume(MongoWriteException.class, error -> error.getError().getCode() == LicenseMongoRepositoryAdapter.DUPLICATE_KEY
                        ? findByNaturalKey(report.getOrganisationId(), report.getLicenseId(), report.getMeasuredAt()).map(existing -> new Stored(existing, false))
                        : Mono.error(error));
    }

    @Override
    public Mono<UsageReport> findByNaturalKey(UUID organisationId, UUID licenseId, Instant measuredAt) {
        return Mono.from(getCollection().find(Filters.and(Filters.eq(FIELD_ORGANISATION, organisationId), Filters.eq(FIELD_LICENSE, licenseId),
                        Filters.eq(FIELD_MEASURED_AT, measuredAt))).first())
                .map(UsageReportPersistenceMapper::toDomain);
    }

    @Override
    public Mono<UsageReport> findById(UUID id, UUID organisationId) {
        return Mono.from(getCollection().find(Filters.and(Filters.eq(FIELD_ID, id), Filters.eq(FIELD_ORGANISATION, organisationId))).first())
                .map(UsageReportPersistenceMapper::toDomain);
    }

    @Override
    public Flux<UsageReport> findAll(UUID organisationId, UUID licenseId, int limit) {
        Bson filter = licenseId == null ? Filters.eq(FIELD_ORGANISATION, organisationId)
                : Filters.and(Filters.eq(FIELD_ORGANISATION, organisationId), Filters.eq(FIELD_LICENSE, licenseId));
        return Flux.from(getCollection().find(filter).sort(Sorts.descending(FIELD_MEASURED_AT)).limit(limit)).map(UsageReportPersistenceMapper::toDomain);
    }

}
