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

package io.spine.server.storage.datastore.record;

import com.google.cloud.datastore.Key;
import com.google.protobuf.Message;
import io.spine.server.storage.RecordSpec;
import io.spine.server.storage.datastore.DatastoreMedium;
import io.spine.server.storage.datastore.Kind;
import io.spine.server.storage.datastore.config.FlatLayout;
import io.spine.server.storage.datastore.config.RecordLayout;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Tells how to structure Datastore Entities used to persist Protobuf messages.
 *
 * <p>Incorporates the knowledge of both Datastore Entity structure
 * and the ancestor-children relations between Entities.
 *
 *
 */
public final class DsEntitySpec<I, R extends Message> {

    private final RecordSpec<I, R, ?> recordSpec;
    private final RecordLayout<I, R> layout;

    //TODO:2021-04-15:alex.tymchenko: move `kind` here.

    /**
     * Creates a new instance of the Datastore Entity specification.
     *
     * @param recordSpec
     *         specification telling which fields of Protobuf message to store in Datastore Entity
     * @param layout
     *         ancestor-children structure to use for Datastore Entities
     */
    public DsEntitySpec(RecordSpec<I, R, ?> recordSpec, RecordLayout<I, R> layout) {
        this.recordSpec = checkNotNull(recordSpec);
        this.layout = checkNotNull(layout);
    }

    /**
     * Creates a new instance of Datastore Entity specification with the flat Entity relations
     * (i.e. no ancestor-children hierarchy).
     *
     * @param recordSpec
     *         specification telling which fields of Protobuf message to store in Datastore Entity
     */
    public DsEntitySpec(RecordSpec<I, R, ?> recordSpec) {
        this(recordSpec, new FlatLayout<>(recordSpec.sourceType()));
    }

    /**
     * Returns the storage specification for the persisted Protobuf message.
     */
    public RecordSpec<I, R, ?> recordSpec() {
        return recordSpec;
    }

    /**
     * Returns the Datastore Entity layout in terms of ancestor-children hierarchy.
     */
    public RecordLayout<I, R> layout() {
        return layout;
    }

    /**
     * Creates a new Datastore Entity key for the passed record identifier.
     *
     * <p>Takes the ancestor-children layout into account.
     *
     * @param id
     *         the identifier of some stored Protobuf record to create Datastore key for
     * @param datastore
     *         Datastore facade
     * @return a new instance of Datastore Entity key
     */
    public Key keyOf(I id, DatastoreMedium datastore) {
        Key key = layout.keyOf(id, datastore);
        return key;
    }

    public Kind kind() {
        return layout.recordKind();
    }
}
