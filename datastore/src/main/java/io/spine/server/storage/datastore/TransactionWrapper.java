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

import com.google.cloud.datastore.DatastoreException;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.Transaction;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A Cloud Datastore transaction wrapper.
 */
public final class TransactionWrapper implements AutoCloseable {

    private final Transaction tx;

    TransactionWrapper(Transaction tx) {
        this.tx = checkNotNull(tx);
    }

    /**
     * Puts the given entity into the Datastore in the transaction.
     */
    public void createOrUpdate(Entity entity) {
        tx.put(entity);
    }

    /**
     * Reads an entity from the Datastore in the transaction.
     *
     * @return the entity with the given key or {@code Optional.empty()} if such an entity does not
     *         exist
     */
    public Optional<Entity> read(Key key) {
        Entity entity = tx.get(key);
        return Optional.ofNullable(entity);
    }

    /**
     * Commits this transaction.
     *
     * @throws DatastoreException if the transaction is no longer active
     */
    public void commit() {
        tx.commit();
    }

    /**
     * Rolls back this transaction.
     *
     * @throws DatastoreException if the transaction is no longer active
     */
    public void rollback() {
        tx.rollback();
    }

    /**
     * Rolls back this transaction if it's still active.
     */
    @Override
    public void close() {
        if (tx.isActive()) {
            rollback();
        }
    }
}