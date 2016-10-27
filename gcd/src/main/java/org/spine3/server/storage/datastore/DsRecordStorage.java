/*
 * Copyright 2015, TeamDev Ltd. All rights reserved.
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

package org.spine3.server.storage.datastore;

import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.Query;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Any;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.FieldMask;
import com.google.protobuf.Message;
import javafx.util.Pair;
import org.spine3.protobuf.AnyPacker;
import org.spine3.protobuf.TypeUrl;
import org.spine3.server.entity.FieldMasks;
import org.spine3.server.storage.EntityStorageRecord;
import org.spine3.server.storage.RecordStorage;

import javax.annotation.Nullable;
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * {@link RecordStorage} implementation based on Google App Engine Datastore.
 *
 * @author Alexander Litus
 * @author Dmytro Dashenkov
 * @see DatastoreStorageFactory
 * @see LocalDatastoreStorageFactory
 */
class DsRecordStorage<I> extends RecordStorage<I> {

    private final DatastoreWrapper datastore;
    private final TypeUrl typeUrl;

    private static final String VERSION_KEY = "version";
    private static final TypeUrl RECORD_TYPE_URL = TypeUrl.of(EntityStorageRecord.class);
    private static final String ID_CONVERTION_ERROR_MESSAGE
            = "Entity had ID of an invalid type; could not parse ID from String. Note: custom convection is not supported. See Identifiers#idToString.";

    /* package */
    static <I> DsRecordStorage<I> newInstance(Descriptor descriptor, DatastoreWrapper datastore, boolean multitenant) {
        return new DsRecordStorage<>(descriptor, datastore, multitenant);
    }

    private static final Function<Entity, EntityStorageRecord> recordFromEntity = new Function<Entity, EntityStorageRecord>() {
        @Nullable
        @Override
        public EntityStorageRecord apply(@Nullable Entity input) {
            if (input == null) {
                return null;
            }

            final EntityStorageRecord record = Entities.entityToMessage(input, RECORD_TYPE_URL);
            return record;
        }
    };

    /**
     * Creates a new storage instance.
     *
     * @param descriptor the descriptor of the type of messages to save to the storage.
     * @param datastore  the datastore implementation to use.
     */
    private DsRecordStorage(Descriptor descriptor, DatastoreWrapper datastore, boolean multitenant) {
        super(multitenant);
        this.typeUrl = TypeUrl.of(descriptor);
        this.datastore = datastore;
    }

    @Nullable
    @Override
    protected EntityStorageRecord readRecord(I id) {
        final Key key = createKey(id);
        final Entity response = datastore.read(key);

        if (response == null) {
            return EntityStorageRecord.getDefaultInstance();
        }

        return Entities.entityToMessage(response, RECORD_TYPE_URL);
    }

    @Override
    protected Iterable<EntityStorageRecord> readMultipleRecords(Iterable<I> ids) {
        return lookup(ids, recordFromEntity);
    }

    @Override
    protected Iterable<EntityStorageRecord> readMultipleRecords(Iterable<I> ids, final FieldMask fieldMask) {
        final Function<Entity, EntityStorageRecord> transformer = new Function<Entity, EntityStorageRecord>() {
            @Nullable
            @Override
            public EntityStorageRecord apply(@Nullable Entity input) {
                if (input == null) {
                    return null;
                }

                final EntityStorageRecord readRecord = Entities.entityToMessage(input, RECORD_TYPE_URL);
                final Message state = AnyPacker.unpack(readRecord.getState());
                final Message maskedState = FieldMasks.applyMask(fieldMask, state, typeUrl);
                final Any wrappedState = AnyPacker.pack(maskedState);

                final EntityStorageRecord record = EntityStorageRecord.newBuilder(readRecord)
                        .setState(wrappedState)
                        .build();
                return record;
            }
        };

        return lookup(ids, transformer);
    }

    @Override
    protected Map<I, EntityStorageRecord> readAllRecords() {
        return readAllRecords(FieldMask.getDefaultInstance());
    }

    @Override
    protected Map<I, EntityStorageRecord> readAllRecords(final FieldMask fieldMask) {
        final Function<Entity, Pair<I, EntityStorageRecord>> mapper
                = new Function<Entity, Pair<I, EntityStorageRecord>>() {
            @Nullable
            @Override
            public Pair<I, EntityStorageRecord> apply(@Nullable Entity input) {
                if (input == null) {
                    return null;
                }
                // Retrieve ID
                final I id = IdTransformer.idFromString(input.key().name(), this.getClass());
                checkState(id != null, ID_CONVERTION_ERROR_MESSAGE);

                // Retrieve record
                EntityStorageRecord record = Entities.entityToMessage(input, RECORD_TYPE_URL);
                final Any packedState = record.getState();
                Message state = AnyPacker.unpack(packedState);
                state = FieldMasks.applyMask(fieldMask, state, typeUrl);
                record = EntityStorageRecord.newBuilder(record)
                        .setState(AnyPacker.pack(state))
                        .build();

                return new Pair<>(id, record);
            }
        };

        return queryAll(mapper, FieldMask.getDefaultInstance());
    }

    private Iterable<EntityStorageRecord> lookup(
            Iterable<I> ids,
            Function<Entity, EntityStorageRecord> transformer) {

        final Collection<Key> keys = new LinkedList<>();
        for (I id : ids) {
            final Key key = createKey(id);
            keys.add(key);
        }

        final List<Entity> results = datastore.read(keys);
        final Collection<EntityStorageRecord> records = Collections2.transform(results, transformer);

        return Collections.unmodifiableCollection(records);
    }

    private Map<I, EntityStorageRecord> queryAll(Function<Entity, Pair<I, EntityStorageRecord>> transformer, FieldMask fieldMask) {
        final String sql = "SELECT * FROM " + RECORD_TYPE_URL.getSimpleName();
        final Query<?> query = Query.gqlQueryBuilder(sql).build();
        final List<Entity> results = datastore.read(query);

        final ImmutableMap.Builder<I, EntityStorageRecord> records = new ImmutableMap.Builder<>();
        for (Entity entity : results) {
            final Pair<I, EntityStorageRecord> recordPair = transformer.apply(entity);
            checkNotNull(recordPair, "Datastore may not contain null records.");
            final EntityStorageRecord fullRecord = recordPair.getValue();
            final Message fullState = AnyPacker.unpack(fullRecord.getState());
            final Message maskedState = FieldMasks.applyMask(fieldMask, fullState, typeUrl);
            final EntityStorageRecord maskedRecord = EntityStorageRecord.newBuilder(fullRecord)
                    .setState(AnyPacker.pack(maskedState))
                    .build();
            records.put(recordPair.getKey(), maskedRecord);
        }

        return records.build();
    }

    @Override
    protected void writeRecord(I id, EntityStorageRecord entityStorageRecord) {
        checkNotNull(id, "ID is null.");
        checkNotNull(entityStorageRecord, "Message is null.");

        final Key key = createKey(id);
        final Entity incompleteEntity = Entities.messageToEntity(entityStorageRecord, key);
        final Entity.Builder entity = Entity.builder(incompleteEntity);
        entity.set(VERSION_KEY, entityStorageRecord.getVersion());
        datastore.createOrUpdate(entity.build());
    }

    private Key createKey(I id) {
        final String idString = IdTransformer.idToString(id);
        return datastore.getKeyFactory(RECORD_TYPE_URL.getSimpleName()).newKey(idString);
    }
}
