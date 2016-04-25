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

import com.google.protobuf.Any;
import com.google.protobuf.Message;
import org.spine3.protobuf.Messages;
import org.spine3.type.TypeName;

import javax.annotation.Nullable;

import static com.google.api.services.datastore.DatastoreV1.*;
import static com.google.api.services.datastore.client.DatastoreHelper.makeKey;
import static org.spine3.server.storage.datastore.DatastoreWrapper.entityToMessage;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.spine3.server.storage.datastore.DatastoreWrapper.messageToEntity;

/**
 * Special Storage type for storing and retrieving global properties with unique keys.
 *
 * @author Mikhail Mikhaylov
 */
/* package */ class DsPropertyStorage {

    private static final String PROPERTIES_KIND = "Properties-Kind";
    private static final String ANY_TYPE_URL = TypeName.of(Any.getDescriptor()).toTypeUrl();

    private final DatastoreWrapper datastore;

    /* package */ static DsPropertyStorage newInstance(DatastoreWrapper datastore) {
        return new DsPropertyStorage(datastore);
    }

    private DsPropertyStorage(DatastoreWrapper datastore) {
        this.datastore = datastore;
    }

    /* package */ <V extends Message> void write(String propertyId, V value) {
        checkNotNull(propertyId, "Property Id is null.");
        checkNotNull(value, "Property value is null.");

        final Key.Builder key = makeKey(PROPERTIES_KIND, propertyId);
        final Entity.Builder entity = messageToEntity(Messages.toAny(value), key);
        final Mutation.Builder mutation = Mutation.newBuilder().addUpsert(entity);
        datastore.commit(mutation);
    }

    @Nullable
    /* package */ <V extends Message> V read(String propertyId) {
        final Key.Builder key = makeKey(PROPERTIES_KIND, propertyId);
        final LookupRequest request = LookupRequest.newBuilder().addKey(key).build();

        final LookupResponse response = datastore.lookup(request);

        if (response == null || response.getFoundCount() == 0) {
            return null;
        }

        final EntityResult entity = response.getFound(0);
        final Any anyResult = entityToMessage(entity, ANY_TYPE_URL);
        return Messages.fromAny(anyResult);
    }
}