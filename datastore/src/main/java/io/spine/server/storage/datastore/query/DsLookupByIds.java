/*
 * Copyright 2021, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

package io.spine.server.storage.datastore.query;

import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.Key;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.FieldMask;
import com.google.protobuf.Message;
import io.spine.query.RecordQuery;
import io.spine.server.storage.datastore.DatastoreMedium;
import io.spine.server.storage.datastore.record.DsEntitySpec;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Streams.stream;
import static io.spine.server.storage.datastore.record.DsEntityComparator.implementing;
import static io.spine.server.storage.datastore.record.Entities.toMessage;
import static io.spine.server.storage.datastore.record.FieldMaskApplier.recordMasker;

/**
 * An {@code Entity} lookup in Google Datastore using {@code Entity} identifiers.
 *
 * @implNote Lookup is performed by reading all the entities with Datastore Keys matching
 *         the provided IDs first and then applying other query constraints in-memory.
 */
final class DsLookupByIds<I, R extends Message> extends PreparedQuery<I, R> {

    private final DatastoreMedium datastore;

    /**
     * Creates a lookup for a passed {@code RecordQuery}.
     *
     * @param datastore
     *         Datastore connector
     * @param query
     *         a query to create this lookup for
     * @param adapter
     *         an adapter of values of {@code RecordQuery} parameters to Datastore-native types
     * @param spec
     *         Entity specification of the queried records
     */
    DsLookupByIds(DatastoreMedium datastore,
                  RecordQuery<I, R> query,
                  FilterAdapter adapter,
                  DsEntitySpec<I, R> spec) {
        super(query, adapter, spec);
        this.datastore = datastore;
    }

    @Override
    IntermediateResult fetchFromDatastore() {
        List<@Nullable Entity> rawEntities = readList(identifiers());
        return new IntermediateResult(rawEntities);
    }

    @Override
    Iterable<R> toRecords(IntermediateResult intermediateResult) {
        List<@Nullable Entity> rawEntities = intermediateResult.entities();
        Predicate<Entity> predicate = columnPredicate();
        Stream<@Nullable Entity> stream = rawEntities
                .stream()
                .filter(Objects::nonNull)
                .filter(predicate);
        if (hasSorting()) {
            stream = stream.sorted(implementing(sorting()));
        }
        Stream<R> recordStream = stream.map(toMaskedRecord(mask()));
        if (limit() != null && limit() > 0) {
            recordStream = recordStream.limit(limit());
        }
        ImmutableList<R> result = recordStream.collect(toImmutableList());
        return result;
    }

    private Predicate<Entity> columnPredicate() {
        if (!(predicate().allParams()
                         .isEmpty() && predicate().children()
                                                  .isEmpty())) {
            return new ColumnPredicate<>(query().subject(), columnAdapter());
        }
        return entity -> true;
    }

    private List<@Nullable Entity> readList(Iterable<I> ids) {
        ImmutableList<Key> keys = toKeys(ids);
        List<@Nullable Entity> entities = datastore.lookup(keys);
        return entities;
    }

    private ImmutableList<Key> toKeys(Iterable<I> ids) {
        ImmutableList<Key> keys = stream(ids)
                .map(id -> spec().keyOf(id, datastore))
                .collect(toImmutableList());
        return keys;
    }

    private Function<Entity, R> toMaskedRecord(FieldMask mask) {
        Function<R, R> masker = recordMasker(mask);
        return entity -> {
            R record = toMessage(entity, recordType());
            R maskedRecord = masker.apply(record);
            return maskedRecord;
        };
    }
}
