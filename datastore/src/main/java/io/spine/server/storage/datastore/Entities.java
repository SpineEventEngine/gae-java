/*
 * Copyright 2019, TeamDev. All rights reserved.
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

import com.google.cloud.datastore.Blob;
import com.google.cloud.datastore.BlobValue;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.Key;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.Internal;
import com.google.protobuf.Message;
import io.spine.server.entity.EntityRecord;
import io.spine.type.TypeUrl;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static io.spine.protobuf.AnyPacker.unpack;
import static io.spine.server.storage.StateField.bytes;

/**
 * Utility class for converting {@linkplain Message proto messages} into
 * {@linkplain Entity entities} and vise versa.
 */
final class Entities {

    static final TypeUrl RECORD_TYPE_URL = TypeUrl.of(EntityRecord.class);

    /** Prevent utility class instantiation. */
    private Entities() {
    }

    /**
     * Retrieves a message of given type, assignable from {@code Message}, from an {@link Entity}.
     *
     * <p>If passed {@link Entity} is {@code null}, a default instance for the given type
     * is returned.
     *
     * @param entity
     *         source {@link Entity} to get message form
     * @param type
     *         {@link TypeUrl} of required message
     * @param <M>
     *         required message type
     * @return message contained in the {@link Entity}
     */
    @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"} /* Rely on caller. */)
    static <M extends Message> M toMessage(@Nullable Entity entity, TypeUrl type) {
        if (entity == null) {
            return defaultMessage(type);
        }
        Any wrapped = toAny(entity, type);
        M result = (M) unpack(wrapped);
        return result;
    }

    private static Any toAny(Entity entity, TypeUrl type) {
        String typeName = type.value();
        Blob value = entity.getBlob(bytes.toString());
        ByteString valueBytes = ByteString.copyFrom(value.toByteArray());
        Any result = Any
                .newBuilder()
                .setValue(valueBytes)
                .setTypeUrl(typeName)
                .build();
        return result;
    }

    /**
     * Retrieves a {@link Function} transforming {@linkplain Entity Entities} into
     * {@linkplain Message Messages} of the given type.
     *
     * @param type
     *         the desired type of the messages
     * @param <M>
     *         the compile-time type of the messages
     * @return a {@link Function} transforming {@linkplain Entity Entities} into
     *         {@linkplain Message Messages}
     */
    static <M extends Message> Function<Entity, M> toMessage(TypeUrl type) {
        return entity -> toMessage(entity, type);
    }

    /**
     * Generates an {@link Entity} with given {@link Key} and from given proto {@code Message}.
     *
     * @param message
     *         source of data to be put into the {@link Entity}
     * @param key
     *         instance of {@link Key} to be assigned to the {@link Entity}
     * @return new instance of {@link Entity} containing serialized proto message
     */
    static Entity fromMessage(Message message, Key key) {
        Entity.Builder builder = builderFromMessage(message, key);
        Entity entity = builder.build();
        return entity;
    }

    /**
     * Creates an incomplete {@link Entity.Builder } with given {@link Key} and from given
     * proto {@code Message}.
     *
     * @param message
     *         source of data to be put into the {@link Entity}
     * @param key
     *         instance of {@link Key} to be assigned to the {@link Entity}
     * @return new instance of {@code Entity.Builder} containing serialized proto message
     */
    static Entity.Builder builderFromMessage(Message message, Key key) {
        checkNotNull(message);
        checkNotNull(key);

        byte[] messageBytes = message.toByteArray();
        Blob valueBlob = Blob.copyFrom(messageBytes);
        BlobValue blobValue = BlobValue
                .newBuilder(valueBlob)
                .setExcludeFromIndexes(true)
                .build();
        Entity.Builder builder = Entity
                .newBuilder(key)
                .set(bytes.toString(), blobValue);
        return builder;
    }

    @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"} /* Rely on caller. */)
    private static <M extends Message> M defaultMessage(TypeUrl type) {
        Class<M> messageClass = (Class<M>) type.toJavaClass();
        checkState(messageClass != null,
                   "Not found class for type url \"%s\". Try to rebuild the project.",
                   type.toTypeName()
                       .value());
        M message = Internal.getDefaultInstance(messageClass);
        return message;
    }

    static @Nullable EntityRecord nullableToRecord(@Nullable Entity entity) {
        if (entity == null) {
            return null;
        }
        return toRecord(entity);
    }

    static EntityRecord toRecord(Entity entity) {
        checkNotNull(entity);
        return toMessage(entity, RECORD_TYPE_URL);
    }
}
