/*
 * Copyright 2017, TeamDev Ltd. All rights reserved.
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
import com.google.cloud.datastore.EntityQuery;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.Query;
import com.google.cloud.datastore.StructuredQuery;
import com.google.common.base.Function;
import com.google.common.collect.Iterators;
import com.google.common.reflect.TypeToken;
import org.spine3.base.Stringifiers;

import javax.annotation.Nullable;
import java.util.Iterator;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A utility for generating the {@linkplain org.spine3.server.storage.Storage#index() storage ID indexes}.
 *
 * @author Dmytro Dashenkov.
 * @see org.spine3.server.storage.Storage#index()
 */
public class Indexes {

    private Indexes() {
    }

    /**
     * Retrieves the ID index for the given {@code kind} or the storage records.
     *
     * <p>A call to this method is equivalent to {@code indexIterator(datastore, kind, idClass, null)}.
     *
     * @param datastore datastore to get the indexes for
     * @param kind      the kind if the records in the datastore
     * @param idClass   {@link Class} of the record ID
     * @param <I>       type of the IDs to retrieve
     * @return an {@link Iterator} of the IDs matching given record kind
     * @see #indexIterator(DatastoreWrapper, String, Class, StructuredQuery.Filter)
     */
    public static <I> Iterator<I> indexIterator(DatastoreWrapper datastore,
                                                String kind,
                                                Class<I> idClass) {
        return indexIterator(datastore, kind, idClass, null);
    }

    /**
     * Retrieves the ID index for the given {@code kind} or the storage records.
     *
     * @param datastore datastore to get the indexes for
     * @param kind      the kind if the records in the datastore
     * @param idClass   {@link Class} of the record ID
     * @param filter    custom filters for selecting only a subset of the records of given type from datastore;
     *                  {@code null} stands for no custom filters
     * @param <I>       type of the IDs to retrieve
     * @return an {@link Iterator} of the IDs matching given record kind and filter
     */
    public static <I> Iterator<I> indexIterator(DatastoreWrapper datastore,
                                                String kind,
                                                Class<I> idClass,
                                                @Nullable StructuredQuery.Filter filter) {
        checkNotNull(datastore, "datastore");
        checkNotNull(kind, "kind");
        checkNotNull(idClass, "idClass");

        final EntityQuery.Builder query = Query.newEntityQueryBuilder()
                                               .setKind(kind);
        if (filter != null) {
            query.setFilter(filter);
        }
        final Iterable<Entity> allEntities = datastore.read(query.build());
        final Iterator<I> idIterator = Iterators.transform(allEntities.iterator(), idExtractor(idClass));
        return idIterator;
    }

    private static <I> Function<Entity, I> idExtractor(final Class<I> idClass) {
        return new Function<Entity, I>() {
            @Override
            public I apply(@Nullable Entity input) {
                checkNotNull(input);
                final Key key = input.getKey();
                final String stringId = key.getName();
                final I id = Stringifiers.parse(stringId, TypeToken.of(idClass));
                return id;
            }
        };
    }
}
