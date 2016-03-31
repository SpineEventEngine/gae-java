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
import com.google.common.collect.Iterators;
import org.spine3.base.Event;
import org.spine3.base.EventId;
import org.spine3.server.event.EventStreamQuery;
import org.spine3.server.storage.EntityStorageRecord;
import org.spine3.server.storage.EventStorage;
import org.spine3.server.storage.EventStorageRecord;
import org.spine3.type.TypeName;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.List;

import static com.google.api.services.datastore.DatastoreV1.*;
import static com.google.api.services.datastore.client.DatastoreHelper.makeKey;
import static org.spine3.server.storage.datastore.DatastoreWrapper.*;

/**
 * Storage for event records based on Google Cloud Datastore.
 *
 * @author Alexander Litus
 * @see DatastoreStorageFactory
 * @see LocalDatastoreStorageFactory
 */
class DsEventStorage extends EventStorage {

    private final DatastoreWrapper datastore;
    private static final String KIND = EventStorageRecord.class.getName();
    private static final String TYPE_URL = TypeName.of(EntityStorageRecord.getDescriptor()).toTypeUrl();

    protected static DsEventStorage newInstance(DatastoreWrapper datastore) {
        return new DsEventStorage(datastore);
    }

    private DsEventStorage(DatastoreWrapper datastore) {
        this.datastore = datastore;
    }

    @Override
    public Iterator<Event> iterator(EventStreamQuery eventStreamQuery) {

        final Query.Builder query = makeQuery(eventStreamQuery, KIND);
        final List<EntityResult> entityResults = datastore.runQuery(query);
        final List<EventStorageRecord> records = entitiesToMessages(entityResults, TYPE_URL);
        // TODO:2016-03-30:mikhail.mikhaylov: Should use some pagination for data, because now we load ALL of it here
        final Iterator<Event> iterator = Iterators.transform(records.iterator(), TO_EVENT);
        return iterator;
    }

    @Override
    protected void writeInternal(EventStorageRecord record) {
        final Key.Builder key = makeKey(KIND, record.getEventId());
        final Entity.Builder entity = messageToEntity(record, key);
        entity.addProperty(makeTimestampProperty(record.getTimestamp()));

        final Mutation.Builder mutation = Mutation.newBuilder().addInsert(entity);
        datastore.commit(mutation);
    }

    @Nullable
    @Override
    protected EventStorageRecord readInternal(EventId eventId) {
        return null;
    }

    private static final Function<EventStorageRecord, Event> TO_EVENT = new Function<EventStorageRecord, Event>() {
        @Override
        public Event apply(@Nullable EventStorageRecord input) {
            if (input == null) {
                return Event.getDefaultInstance();
            }
            final Event result = toEvent(input);
            return result;
        }
    };
}
