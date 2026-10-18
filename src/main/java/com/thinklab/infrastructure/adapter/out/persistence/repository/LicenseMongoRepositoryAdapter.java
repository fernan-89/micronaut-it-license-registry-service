package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.thinklab.domain.exception.DuplicateLicenseException;
import com.thinklab.domain.exception.InvalidLicenseStatusException;
import com.thinklab.domain.model.License;
import com.thinklab.domain.model.License.LicenseAuditEntry;
import com.thinklab.domain.model.License.LicenseStatus;
import com.thinklab.domain.model.UsageReport;
import com.thinklab.domain.repository.LicenseRepository;
import com.thinklab.infrastructure.adapter.out.persistence.entity.LicenseDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.LicenseDocument.AuditEntryDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.LicenseDocument.LicensePersistenceMapper;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Singleton;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * MongoDB Reactive Repository Adapter for the License aggregate, raw reactive-streams driver. Every change is one atomic update guarded by
 * the status loaded (ADR-033) that also appends the audit entry; a name unique per organisation is the atomic backstop of a duplicate. The
 * latest report only ever moves forward in time (a compare on the moment), so it does not matter in
 * which order, or how many times, the reports arrive (ADR-030).
 */
@Singleton
public class LicenseMongoRepositoryAdapter implements LicenseRepository {

    private static final Logger log = LoggerFactory.getLogger(LicenseMongoRepositoryAdapter.class);
    static final String DEFAULT_DATABASE = "thinklab_it_license_registry_db";
    static final String COLLECTION_NAME = "licenses";
    static final int DUPLICATE_KEY = 11000;
    private static final String FIELD_ID = "_id";
    private static final String FIELD_ORGANISATION = "organisationId";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_LAST_MEASURED = "lastMeasuredAt";

    private static final CodecRegistry POJO_CODEC_REGISTRY = CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build())
    );

    private final MongoClient mongoClient;
    private final String database;

    public LicenseMongoRepositoryAdapter(MongoClient mongoClient, @Property(name = "mongodb.uri") String mongoUri) {
        this.mongoClient = mongoClient;
        String configured = new ConnectionString(Objects.requireNonNull(mongoUri, "mongodb.uri cannot be null.")).getDatabase();
        this.database = configured != null ? configured : DEFAULT_DATABASE;
    }

    private MongoCollection<LicenseDocument> getCollection() {
        return mongoClient.getDatabase(database).getCollection(COLLECTION_NAME, LicenseDocument.class).withCodecRegistry(POJO_CODEC_REGISTRY);
    }

    @Override
    public Mono<License> create(License license) {
        log.debug("[PERSISTENCE] Monolithic create for License Aggregate: {}", license.getId());

        return Mono.from(getCollection().insertOne(LicensePersistenceMapper.toDocument(license)))
                .map(result -> license)
                .onErrorMap(MongoWriteException.class, error -> duplicate(error, license));
    }

    @Override
    public Mono<License> findById(UUID id, UUID organisationId) {
        return Mono.from(getCollection().find(Filters.and(Filters.eq(FIELD_ID, id), Filters.eq(FIELD_ORGANISATION, organisationId))).first())
                .map(LicensePersistenceMapper::toDomain);
    }

    @Override
    public Flux<License> findAll(UUID organisationId) {
        return Flux.from(getCollection().find(Filters.eq(FIELD_ORGANISATION, organisationId)).sort(Sorts.ascending("createdAt"))).map(LicensePersistenceMapper::toDomain);
    }

    @Override
    public Mono<Void> save(License license, LicenseStatus expectedStatus, LicenseAuditEntry auditEntry) {
        Bson guard = Filters.and(Filters.eq(FIELD_ID, license.getId()), Filters.eq(FIELD_ORGANISATION, license.getOrganisationId()), Filters.eq(FIELD_STATUS, expectedStatus.name()));
        Bson update = Updates.combine(
                Updates.set("product", license.getProduct()),
                Updates.set("unit", license.getUnit().name()),
                Updates.set("entitled", license.getEntitled()),
                Updates.set("warnPercent", license.getWarnPercent()),
                Updates.set("reportEveryHours", license.getReportEveryHours()),
                Updates.set("vendorId", license.getVendorId()),
                Updates.set("agreementId", license.getAgreementId()),
                Updates.set(FIELD_STATUS, license.getStatus().name()),
                Updates.set("updatedAt", Instant.now()),
                Updates.push("auditTrail", AuditEntryDocument.fromDomain(auditEntry))
        );
        return Mono.from(getCollection().updateOne(guard, update))
                .flatMap(result -> result.getMatchedCount() == 0
                        ? Mono.error(new InvalidLicenseStatusException("License was changed by someone else while this change was being recorded; read it again and retry."))
                        : Mono.<Void>empty())
                .onErrorMap(MongoWriteException.class, error -> duplicate(error, license));
    }

    @Override
    public Mono<Void> recordReport(UUID licenseId, UUID organisationId, UsageReport report) {
        Bson newer = Filters.and(Filters.eq(FIELD_ID, licenseId), Filters.eq(FIELD_ORGANISATION, organisationId),
                Filters.or(Filters.eq(FIELD_LAST_MEASURED, null), Filters.lt(FIELD_LAST_MEASURED, report.getMeasuredAt())));
        Bson latest = Updates.combine(Updates.set(FIELD_LAST_MEASURED, report.getMeasuredAt()), Updates.set("lastInUse", report.getInUse()));
        return Mono.from(getCollection().updateOne(newer, latest)).then();
    }

    private static Throwable duplicate(MongoWriteException error, License license) {
        return error.getError().getCode() == DUPLICATE_KEY
                ? new DuplicateLicenseException("A licence for '" + license.getProduct() + "' already exists in this organisation.") : error;
    }
}
