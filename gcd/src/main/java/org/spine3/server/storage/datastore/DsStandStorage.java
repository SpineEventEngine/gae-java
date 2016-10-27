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

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.*;
import com.google.protobuf.FieldMask;
import org.spine3.base.Identifiers;
import org.spine3.protobuf.KnownTypes;
import org.spine3.protobuf.TypeUrl;
import org.spine3.server.stand.AggregateStateId;
import org.spine3.server.storage.EntityStorageRecord;
import org.spine3.server.storage.StandStorage;
import org.spine3.type.ClassName;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import static com.google.common.base.Preconditions.*;

/**
 * @author Dmytro Dashenkov
 */
/*package*/ class DsStandStorage extends StandStorage {

    private static final Function<AggregateStateId, String> ID_TRANSFORMER = new Function<AggregateStateId, String>() {
        @Override
        public String apply(@Nullable AggregateStateId input) {
            checkNotNull(input);
            final Object id = input.getAggregateId();
            return Identifiers.idToString(id);
        }
    };

    private final DsRecordStorage<String> recordStorage;

    /*package*/
    static StandStorage newInstance(boolean multitenant, DsRecordStorage<String> recordStorage) {
        return new DsStandStorage(multitenant, recordStorage);
    }

    private DsStandStorage(boolean multitenant, DsRecordStorage<String> recordStorage) {
        super(multitenant);
        this.recordStorage = recordStorage;
    }

    @Override
    public ImmutableCollection<EntityStorageRecord> readAllByType(TypeUrl type) {
        final Map<?, EntityStorageRecord> records = recordStorage.readAll();
        final Collection<EntityStorageRecord> recordValues = records.values();
        final Collection<EntityStorageRecord> filteredRecordValues = Collections2.filter(
                recordValues,
                typePredicate(type));
        return ImmutableList.copyOf(filteredRecordValues);
    }

    @Override
    public ImmutableCollection<EntityStorageRecord> readAllByType(TypeUrl type, FieldMask fieldMask) {
        final Map<?, EntityStorageRecord> records = recordStorage.readAll(fieldMask);
        final Collection<EntityStorageRecord> recordValues = records.values();
        final Collection<EntityStorageRecord> filteredRecordValues = Collections2.filter(
                recordValues,
                typePredicate(type));
        return ImmutableList.copyOf(filteredRecordValues);
    }

    @SuppressWarnings("ConstantConditions") // stringId is formally nullable, but it's not effectively
    @Nullable
    @Override
    protected EntityStorageRecord readRecord(AggregateStateId id) {
        final String stringId = ID_TRANSFORMER.apply(id);
        return recordStorage.read(stringId);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Iterable<EntityStorageRecord> readMultipleRecords(Iterable<AggregateStateId> ids) {
        return recordStorage.readMultiple(transformIds(ids));
    }

    @Override
    protected Iterable<EntityStorageRecord> readMultipleRecords(Iterable<AggregateStateId> ids, FieldMask fieldMask) {
        final Iterable<String> stringIds = transformIds(ids);
        return recordStorage.readMultiple(stringIds, fieldMask);
    }

    @Override
    protected Map<AggregateStateId, EntityStorageRecord> readAllRecords() {
        return readAllRecords(FieldMask.getDefaultInstance());
    }

    @Override
    protected Map<AggregateStateId, EntityStorageRecord> readAllRecords(FieldMask fieldMask) {
        final Map<String, EntityStorageRecord> readRecords = recordStorage.readAllRecords(fieldMask);
        final Collection<String> sourceIds = readRecords.keySet();
        final Collection<AggregateStateId> ids = Collections2.transform(sourceIds, reverseIdTransformer());

        final Collection<EntityStorageRecord> recordValues = readRecords.values();
        final Iterator<EntityStorageRecord> recordIterator = recordValues.iterator();
        final ImmutableMap.Builder<AggregateStateId, EntityStorageRecord> result = new ImmutableMap.Builder<>();
        for (AggregateStateId id : ids) {
            checkState(recordIterator.hasNext(), "Set of read values is shorter then set of keys.");
            result.put(id, recordIterator.next());
        }
        checkState(!recordIterator.hasNext(), "Set of read values is longer then set of keys.");

        return result.build();
    }

    @SuppressWarnings("ConstantConditions") // stringId is formally nullable, but it's not effectively
    @Override
    protected void writeRecord(AggregateStateId id, EntityStorageRecord record) {
        final String stringId = ID_TRANSFORMER.apply(id);
        recordStorage.write(stringId, record);
    }

    private static Iterable<String> transformIds(Iterable<AggregateStateId> ids) {
        final Iterable<String> stringIds = Iterables.transform(ids, ID_TRANSFORMER);
        return stringIds;
    }

    private static Function<String, AggregateStateId> reverseIdTransformer() {
        return reverseIdTransformer(null);
    }

    private static Function<String, AggregateStateId> reverseIdTransformer(final TypeUrl withType) {
        return new Function<String, AggregateStateId>() {
            @Override
            public AggregateStateId apply(@Nullable String input) {
                checkNotNull(input, "String ID must not be null.");
                checkArgument(!input.isEmpty(), "String ID must not be empty.");

                // TODO:26-10-16:dmytro.dashenkov: Limitation: omly String ids ae supported by stand storage for now.
                return AggregateStateId.of(input, withType);
            }
        };
    }

    private static Predicate<EntityStorageRecord> typePredicate(final TypeUrl type) {
        return new Predicate<EntityStorageRecord>() {
            @Override
            public boolean apply(@Nullable EntityStorageRecord input) {
                if (input == null) {
                    return false;
                }
                final String classnameString = input.getDescriptorForType().getFullName();
                final ClassName typeName = ClassName.of(classnameString);
                final TypeUrl recordType = KnownTypes.getTypeUrl(typeName);

                return type.equals(recordType);
            }
        };
    }
}
