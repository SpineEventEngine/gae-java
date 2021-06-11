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

package io.spine.server.storage.datastore.delivery;

import io.spine.server.NodeId;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.ShardSessionRecord;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Optional;

import static io.spine.base.Time.currentTime;

/**
 * Updates the {@code nodeId} for the {@link ShardSessionRecord} with the specified
 * {@link ShardIndex} if the record has not been picked by anyone.
 *
 * <p>If there is no such a record, creates a new record.
 */
final class UpdateNodeIfAbsent implements RecordUpdate {

    private final ShardIndex index;
    private final NodeId nodeToSet;

    /**
     * Creates the operation for the given shard index and node ID.
     */
    UpdateNodeIfAbsent(ShardIndex index, NodeId node) {
        this.index = index;
        nodeToSet = node;
    }

    @Override
    public Optional<ShardSessionRecord> createOrUpdate(@Nullable ShardSessionRecord previous) {
        if (previous != null && previous.hasPickedBy()) {
            return Optional.empty();
        }
        ShardSessionRecord.Builder builder =
                previous == null
                ? ShardSessionRecord.newBuilder()
                        .setIndex(index)
                : previous.toBuilder();

        ShardSessionRecord updated =
                builder.setPickedBy(nodeToSet)
                       .setWhenLastPicked(currentTime())
                       .vBuild();
        return Optional.of(updated);
    }
}