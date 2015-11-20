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

import org.spine3.server.storage.CommandStorage;
import org.spine3.server.storage.CommandStoreRecord;

import static com.google.api.services.datastore.DatastoreV1.*;
import static com.google.api.services.datastore.client.DatastoreHelper.*;
import static org.spine3.server.storage.datastore.DatastoreWrapper.makeTimestampProperty;
import static org.spine3.server.storage.datastore.DatastoreWrapper.messageToEntity;

/**
 * Storage for command records based on Google Cloud Datastore.
 *
 * @author Alexander Litus
 * @see DatastoreStorageFactory
 * @see LocalDatastoreStorageFactory
 */
class DsCommandStorage extends CommandStorage {

    @SuppressWarnings("DuplicateStringLiteralInspection")
    private static final String COMMAND_ID_PROPERTY_NAME = "commandId";

    private static final String KIND = CommandStoreRecord.class.getName();

    private final DatastoreWrapper datastore;

    protected static CommandStorage newInstance(DatastoreWrapper datastore) {
        return new DsCommandStorage(datastore);
    }

    private DsCommandStorage(DatastoreWrapper datastore) {
        this.datastore = datastore;
    }

    @Override
    protected void write(CommandStoreRecord record) {

        final Value.Builder id = makeValue(record.getCommandId());
        final Property.Builder idProperty = makeProperty(COMMAND_ID_PROPERTY_NAME, id);
        final Entity.Builder entity = messageToEntity(record, makeKey(KIND));
        entity.addProperty(idProperty);
        entity.addProperty(makeTimestampProperty(record.getTimestamp()));

        final Mutation.Builder mutation = Mutation.newBuilder().addInsertAutoId(entity);
        datastore.commit(mutation);
    }
}
