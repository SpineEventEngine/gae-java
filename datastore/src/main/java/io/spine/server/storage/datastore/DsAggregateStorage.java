/*
 * Copyright 2018, TeamDev. All rights reserved.
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.spine.server.storage.datastore;

import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.EntityQuery;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.Query;
import com.google.cloud.datastore.StructuredQuery;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Message;
import io.spine.core.Version;
import io.spine.server.aggregate.AggregateEventRecord;
import io.spine.server.aggregate.AggregateEventRecord.KindCase;
import io.spine.server.aggregate.AggregateReadRequest;
import io.spine.server.aggregate.AggregateStorage;
import io.spine.server.entity.LifecycleFlags;
import io.spine.string.Stringifiers;
import io.spine.type.TypeName;
import io.spine.type.TypeUrl;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.Optional;

import static com.google.cloud.datastore.StructuredQuery.PropertyFilter.eq;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterators.transform;
import static io.spine.server.aggregate.storage.AggregateField.aggregate_id;
import static io.spine.server.storage.datastore.DsIdentifiers.keyFor;
import static io.spine.server.storage.datastore.DsIdentifiers.of;
import static io.spine.server.storage.datastore.DsProperties.addAggregateId;
import static io.spine.server.storage.datastore.DsProperties.addVersion;
import static io.spine.server.storage.datastore.DsProperties.addWhenCreated;
import static io.spine.server.storage.datastore.DsProperties.byCreatedTime;
import static io.spine.server.storage.datastore.DsProperties.byRecordType;
import static io.spine.server.storage.datastore.DsProperties.byVersion;
import static io.spine.server.storage.datastore.DsProperties.isArchived;
import static io.spine.server.storage.datastore.DsProperties.isDeleted;
import static io.spine.server.storage.datastore.DsProperties.markAsArchived;
import static io.spine.server.storage.datastore.DsProperties.markAsDeleted;
import static io.spine.server.storage.datastore.DsProperties.markAsSnapshot;
import static io.spine.server.storage.datastore.Entities.entitiesToMessages;
import static io.spine.server.storage.datastore.Entities.messageToEntity;
import static io.spine.util.Exceptions.newIllegalArgumentException;

/**
 * A storage of aggregate root events and snapshots based on Google Cloud Datastore.
 *
 * @author Alexander Litus
 * @author Dmytro Dashenkov
 * @see DatastoreStorageFactory
 */
@SuppressWarnings("WeakerAccess")   // Part of API
public class DsAggregateStorage<I> extends AggregateStorage<I> {

    private static final String EVENTS_AFTER_LAST_SNAPSHOT_PREFIX = "EVENTS_AFTER_SNAPSHOT_";

    /**
     * Prefix for the string IDs of the {@link AggregateEventRecord records} which represent
     * an aggregate snapshot, not an event.
     *
     * <p>The aggregate snapshots are stored under an ID composed from {@code SNAPSHOT} and
     * the aggregate ID.
     */
    private static final String SNAPSHOT = "SNAPSHOT";

    private static final TypeName AGGREGATE_LIFECYCLE_KIND =
            TypeName.from(LifecycleFlags.getDescriptor());
    private static final TypeUrl AGGREGATE_RECORD_TYPE_URL =
            TypeUrl.from(AggregateEventRecord.getDescriptor());

    private final DatastoreWrapper datastore;
    private final DsPropertyStorage propertyStorage;
    private final Class<I> idClass;
    private final TypeName stateTypeName;

    public DsAggregateStorage(DatastoreWrapper datastore,
                              DsPropertyStorage propertyStorage,
                              boolean multitenant,
                              Class<I> idClass,
                              Class<? extends Message> stateClass) {
        super(multitenant);
        this.datastore = datastore;
        this.propertyStorage = propertyStorage;
        this.idClass = idClass;
        this.stateTypeName = TypeName.of(stateClass);
    }

    @Override
    public int readEventCountAfterLastSnapshot(I id) {
        checkNotClosed();
        checkNotNull(id);

        RecordId datastoreId = toRecordId(id);
        Optional<Int32Value> count = propertyStorage.read(datastoreId,
                                                          Int32Value.getDescriptor());
        int countValue = count.map(Int32Value::getValue)
                              .orElse(0);
        return countValue;
    }

    @Override
    public void writeEventCountAfterLastSnapshot(I id, int eventCount) {
        checkNotClosed();
        checkNotNull(id);

        RecordId datastoreId = toRecordId(id);
        propertyStorage.write(datastoreId, Int32Value.newBuilder()
                                                     .setValue(eventCount)
                                                     .build());
    }

    @SuppressWarnings("EnumSwitchStatementWhichMissesCases") // Only valuable cases.
    @Override
    protected void writeRecord(I id, AggregateEventRecord record) {
        checkNotNull(id);

        String stringId = Stringifiers.toString(id);
        String recordId;
        Version version;
        KindCase kind = record.getKindCase();
        switch (kind) {
            case EVENT:
                recordId = Stringifiers.toString(record.getEvent()
                                                       .getId());
                version = record.getEvent()
                                .getContext()
                                .getVersion();
                break;
            case SNAPSHOT:
                recordId = SNAPSHOT + stringId;
                version = record.getSnapshot()
                                .getVersion();
                break;
            default:
                throw newIllegalArgumentException("Invalid kind of AggregateEventRecord \"%s\".",
                                                  record.getKindCase());
        }
        Key key = keyFor(datastore, Kind.of(stateTypeName), of(recordId));
        Entity incompleteEntity = messageToEntity(record, key);
        Entity.Builder builder = Entity.newBuilder(incompleteEntity);
        addAggregateId(builder, stringId);
        addWhenCreated(builder, record.getTimestamp());
        addVersion(builder, version);
        markAsSnapshot(builder, kind == KindCase.SNAPSHOT);
        datastore.createOrUpdate(builder.build());
    }

    /**
     * {@inheritDoc}
     *
     * <p>The resulting iterator will fetch {@linkplain AggregateEventRecord events} batch by batch.
     *
     * <p>Size of the batch is specified by the given {@link AggregateReadRequest}.
     *
     * @param request the read request
     * @return a new iterator instance
     */
    @Override
    protected Iterator<AggregateEventRecord> historyBackward(AggregateReadRequest<I> request) {
        StructuredQuery<Entity> query = historyBackwardQuery(request);
        Iterator<Entity> eventEntities = datastore.read(query);
        Iterator<AggregateEventRecord> result = entitiesToMessages(eventEntities,
                                                                         AGGREGATE_RECORD_TYPE_URL);
        return result;
    }

    @VisibleForTesting
    EntityQuery historyBackwardQuery(AggregateReadRequest<I> request) {
        checkNotNull(request);
        String idString = Stringifiers.toString(request.getRecordId());
        int limit = request.getBatchSize();
        return Query.newEntityQueryBuilder()
                    .setKind(stateTypeName.value())
                    .setFilter(eq(aggregate_id.toString(),
                                  idString))
                    .setOrderBy(byVersion(),
                                byCreatedTime(),
                                byRecordType())
                    .setLimit(limit)
                    .build();
    }

    /**
     * Generates an identifier of the Datastore record basing on the given {@code Aggregate}
     * identifier.
     *
     * @param id an identifier of the {@code Aggregate}
     * @return the Datastore record ID
     */
    protected RecordId toRecordId(I id) {
        String stringId = Stringifiers.toString(id);
        String datastoreId = EVENTS_AFTER_LAST_SNAPSHOT_PREFIX + stringId;
        return of(datastoreId);
    }

    /**
     * Provides an access to the GAE Datastore with an API, specific to the Spine framework.
     *
     * <p>Allows the customization of the storage behavior in descendants.
     *
     * @return the wrapped instance of Datastore
     */
    protected DatastoreWrapper getDatastore() {
        return datastore;
    }

    /**
     * Provides an access to the {@link DsPropertyStorage}.
     *
     * <p>Allows the customization of the storage behavior in descendants.
     *
     * @return the wrapped instance of Datastore
     */
    protected DsPropertyStorage getPropertyStorage() {
        return propertyStorage;
    }

    @Override
    public com.google.common.base.Optional<LifecycleFlags> readLifecycleFlags(I id) {
        checkNotNull(id);

        Key key = toKey(id);
        Entity entityStateRecord = datastore.read(key);
        if (entityStateRecord == null) {
            return com.google.common.base.Optional.absent();
        }

        boolean archived = isArchived(entityStateRecord);
        boolean deleted = isDeleted(entityStateRecord);

        if (!archived && !deleted) {
            return com.google.common.base.Optional.absent();
        }
        LifecycleFlags flags = LifecycleFlags.newBuilder()
                                                   .setArchived(archived)
                                                   .setDeleted(deleted)
                                                   .build();
        return com.google.common.base.Optional.of(flags);
    }

    @Override
    public void writeLifecycleFlags(I id, LifecycleFlags flags) {
        checkNotNull(id);
        checkNotNull(flags);

        Key key = toKey(id);
        Entity.Builder entityStateRecord = Entity.newBuilder(key);
        markAsArchived(entityStateRecord, flags.getArchived());
        markAsDeleted(entityStateRecord, flags.getDeleted());
        datastore.createOrUpdate(entityStateRecord.build());
    }

    @Override
    public Iterator<I> index() {
        checkNotClosed();

        StructuredQuery<Entity> allQuery = Query.newEntityQueryBuilder()
                                                      .setKind(stateTypeName.value())
                                                      .build();
        Iterator<Entity> allRecords = datastore.read(allQuery);
        Iterator<I> index = transform(allRecords, new IndexTransformer<>(idClass));
        return index;
    }

    private Key toKey(I id) {
        RecordId recordId = toRecordId(id);
        Key key = keyFor(datastore, Kind.of(AGGREGATE_LIFECYCLE_KIND), recordId);
        return key;
    }

    /**
     * A {@linkplain Function} type transforming String IDs into the specified generic type.
     *
     * @param <I> the generic ID type
     */
    private static class IndexTransformer<I> implements Function<Entity, I> {

        private final Class<I> idClass;

        private IndexTransformer(Class<I> idClass) {
            this.idClass = idClass;
        }

        @Override
        public I apply(@Nullable Entity entity) {
            checkNotNull(entity);
            String stringId = entity.getString(aggregate_id.toString());
            return Stringifiers.fromString(stringId, idClass);
        }
    }
}
