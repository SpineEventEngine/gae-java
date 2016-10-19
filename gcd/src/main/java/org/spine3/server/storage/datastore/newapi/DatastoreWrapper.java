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

package org.spine3.server.storage.datastore.newapi;

import com.google.cloud.datastore.*;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;

import static com.google.common.base.Preconditions.checkState;

/**
 * @author Dmytro Dashenkov
 */
public class DatastoreWrapper {

    private static final String ACTIVE_TRANSACTION_CONDITION_MESSAGE = "Transaction should be active.";
    private static final String NOT_ACTIVE_TRANSACTION_CONDITION_MESSAGE = "Transaction should NOT be active.";
    private final Datastore datastore;
    private Transaction activeTransaction;
    private DatastoreReaderWriter actor;
    private KeyFactory keyFactory;

    protected DatastoreWrapper(Datastore datastore) {
        this.datastore = datastore;
        this.actor = datastore;
    }

    public static DatastoreWrapper wrap(Datastore datastore) {
        return new DatastoreWrapper(datastore);
    }


    public void create(Entity entity) {
        actor.put(entity);
    }

    public void update(Entity entity) {
        actor.update(entity);
    }

    public void createOrUpdate(Entity entity) {
        try {
            create(entity);
        } catch (DatastoreException ignored) {
            update(entity);
        }
    }

    public Entity read(Key key) {
        return datastore.get(key);
    }

    // TODO:18-10-16:dmytro.dashenkov: Check datastore#fetch usage.
    public List<Entity> read(Iterable<Key> keys) {
        return Lists.newArrayList(datastore.get(keys));
    }

    @SuppressWarnings("unchecked")
    public List<Entity> read(Query query) {
        final QueryResults results = actor.run(query);
        return Lists.newArrayList(results);
    }

    public void delete(Key... keys) {
        actor.delete(keys);
    }

    public void dropTable(String table) {
        final String sql = String.format("SELECT * FROM %s;", table);
        final Query query = Query.gqlQueryBuilder(sql).build();
        final List<Entity> entities = read(query);
        final Collection<Key> keys = Collections2.transform(entities, new Function<Entity, Key>() {
            @Nullable
            @Override
            public Key apply(@Nullable Entity input) {
                if (input == null) {
                    return null;
                }

                return input.key();
            }
        });

        delete((Key[]) keys.toArray());
    }

    public void startTransaction() {
        checkState(!isTransactionActive(), NOT_ACTIVE_TRANSACTION_CONDITION_MESSAGE);
        activeTransaction = datastore.newTransaction();
        actor = activeTransaction;
    }

    public void commitTransaction() {
        checkState(isTransactionActive(), ACTIVE_TRANSACTION_CONDITION_MESSAGE);
        activeTransaction.commit();
        actor = datastore;
    }

    public void rollbackTransaction() {
        checkState(isTransactionActive(), ACTIVE_TRANSACTION_CONDITION_MESSAGE);
        activeTransaction.rollback();
        actor = datastore;
    }

    public KeyFactory getKeyFactory(String kind) {
        if (keyFactory == null) {
            initKeyFactory(kind);
        }

        return keyFactory;
    }

    private void initKeyFactory(String kind) {
        final KeyFactory incomplete = datastore.newKeyFactory();
        keyFactory = incomplete.kind(kind);
    }

    private boolean isTransactionActive() {
        return activeTransaction != null && activeTransaction.active();
    }
}
