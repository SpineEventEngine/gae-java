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

import com.google.datastore.v1.*;
import org.spine3.base.CommandId;
import org.spine3.base.CommandStatus;
import org.spine3.base.Error;
import org.spine3.base.Failure;
import org.spine3.protobuf.TypeUrl;
import org.spine3.server.storage.CommandStorage;
import org.spine3.server.storage.CommandStorageRecord;

import java.util.Iterator;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.datastore.v1.client.DatastoreHelper.makeKey;
import static org.spine3.base.Identifiers.idToString;
import static org.spine3.server.storage.datastore.DatastoreWrapper.entityToMessage;
import static org.spine3.server.storage.datastore.DatastoreWrapper.messageToEntity;
import static org.spine3.validate.Validate.checkNotDefault;

/**
 * Storage for command records based on Google Cloud Datastore.
 *
 * @author Alexander Litus
 * @see DatastoreStorageFactory
 * @see LocalDatastoreStorageFactory
 */
class DsCommandStorage extends CommandStorage {

    private final DatastoreWrapper datastore;

    private final TypeUrl typeUrl;

    /* package */ static CommandStorage newInstance(DatastoreWrapper datastore, boolean multitenant) {
        return new DsCommandStorage(datastore, multitenant);
    }

    private DsCommandStorage(DatastoreWrapper datastore, boolean multitenant) {
        super(multitenant);
        this.datastore = datastore;
        typeUrl = TypeUrl.of(CommandStorageRecord.getDescriptor());
    }

    @Override
    protected Iterator<CommandStorageRecord> read(CommandStatus status) {
        return null; // TODO:05-10-16:dmytro.dashenkov: Implement.
    }

    @Override
    public void setOkStatus(CommandId commandId) {
        checkNotNull(commandId);

        final CommandStorageRecord updatedRecord = read(commandId)
                .toBuilder()
                .setStatus(CommandStatus.OK)
                .build();
        write(commandId, updatedRecord);
    }

    @Override
    public void updateStatus(CommandId commandId, Error error) {
        checkNotNull(commandId);
        checkNotNull(error);

        final CommandStorageRecord updatedRecord = read(commandId)
                .toBuilder()
                .setStatus(CommandStatus.ERROR)
                .setError(error)
                .build();
        write(commandId, updatedRecord);
    }

    @Override
    public void updateStatus(CommandId commandId, Failure failure) {
        checkNotNull(commandId);
        checkNotNull(failure);

        final CommandStorageRecord updatedRecord = read(commandId)
                .toBuilder()
                .setStatus(CommandStatus.FAILURE)
                .setFailure(failure)
                .build();
        write(commandId, updatedRecord);
    }

    @Override
    public CommandStorageRecord read(CommandId commandId) {
        checkNotClosed();
        checkNotDefault(commandId);

        final String idString = idToString(commandId);
        final Key.Builder key = createKey(idString);
        final LookupRequest request = LookupRequest.newBuilder().addKeys(key).build();

        final LookupResponse response = datastore.lookup(request);

        if (response == null || response.getFoundCount() == 0) {
            return CommandStorageRecord.getDefaultInstance();
        }

        final EntityResult entity = response.getFound(0);
        final CommandStorageRecord result = entityToMessage(entity, typeUrl.value());
        return result;
    }

    @Override
    public void write(CommandId commandId, CommandStorageRecord record) {
        checkNotClosed();
        checkNotDefault(commandId);
        checkNotDefault(record);

        final String idString = idToString(commandId);

        final Key.Builder key = createKey(idString);

        final Entity.Builder entity = messageToEntity(record, key);
        DatastoreProperties.addTimestampProperty(record.getTimestamp(), entity);
        DatastoreProperties.addTimestampNanosProperty(record.getTimestamp(), entity);

        final Mutation.Builder mutation = Mutation.newBuilder().setInsert(entity); // TODO:11-10-16:dmytro.dashenkov: Check update case.
        datastore.commit(mutation);
    }

    private Key.Builder createKey(String idString) {
        return makeKey(typeUrl.getSimpleName(), idString);
    }
}
