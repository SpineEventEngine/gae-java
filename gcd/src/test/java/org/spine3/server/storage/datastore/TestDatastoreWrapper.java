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

import com.google.cloud.datastore.*;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * Custom extension of the {@link DatastoreWrapper} aimed on testing purposes.
 *
 * @author Dmytro Dashenkov
 * @see TestDatastoreStorageFactory
 */
/*package*/ class TestDatastoreWrapper extends DatastoreWrapper {

    // Default time to wait before each read operation to ensure the data is consistent.
    // NOTE: enabled only if {@link #shouldWaitForConsistency} is {@code true}.
    private static final int CONSISTENCY_AWAIT_TIME_MS = 100;
    private static final int CONSISTENCY_AWAIT_ITERATIONS = 5;

    /**
     * Due to eventual consistency, {@link #dropTable(String) is performed iteratively until the table has no records}.
     *
     * This constant represents the maximum number of cleanup attempts before the execution is continued.
     */
    private static final int MAX_CLEANUP_ATTEMPTS = 5;


    private static final Collection<String> kindsCache = new LinkedList<>();

    private final boolean waitForConsistency;

    private TestDatastoreWrapper(Datastore datastore, boolean waitForConsistency) {
        super(datastore);
        this.waitForConsistency = waitForConsistency;
    }

    /*package*/
    static TestDatastoreWrapper wrap(Datastore datastore, boolean waitForConsistency) {
        return new TestDatastoreWrapper(datastore, waitForConsistency);
    }

    @Override
    public KeyFactory getKeyFactory(String kind) {
        kindsCache.add(kind);
        return super.getKeyFactory(kind);
    }

    @Override
    Entity read(Key key) {
        waitForConsistency();
        return super.read(key);
    }

    @Override
    List<Entity> read(Iterable<Key> keys) {
        waitForConsistency();
        return super.read(keys);
    }

    @Override
    List<Entity> read(Query query) {
        waitForConsistency();
        return super.read(query);
    }

    @Override
    void dropTable(String table) {
        if (!waitForConsistency) {
            super.dropTable(table);
        } else {
            dropTableConsistently(table);
        }
    }

    @SuppressWarnings("BusyWait")   // allow Datastore some time between cleanup attempts.
    private void dropTableConsistently(String table) {
        Integer remainingEntityCount = null;
        int cleanupAttempts = 0;

        while ((remainingEntityCount == null || remainingEntityCount > 0) && cleanupAttempts < MAX_CLEANUP_ATTEMPTS) {

            // sleep in between the cleanup attempts.
            if (cleanupAttempts > 0) {
                try {
                    Thread.sleep(CONSISTENCY_AWAIT_TIME_MS);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            final Query query = Query.newEntityQueryBuilder()
                    .setKind(table)
                    .build();
            final List<Entity> entities = read(query);
            remainingEntityCount = entities.size();

            if (remainingEntityCount > 0) {
                final Collection<Key> keys = Collections2.transform(entities, new Function<Entity, Key>() {
                    @Nullable
                    @Override
                    public Key apply(@Nullable Entity input) {
                        if (input == null) {
                            return null;
                        }

                        return input.getKey();
                    }
                });

                final Key[] keysArray = new Key[keys.size()];
                keys.toArray(keysArray);
                delete(keysArray);

                cleanupAttempts++;
            }
        }

        if (cleanupAttempts >= MAX_CLEANUP_ATTEMPTS && remainingEntityCount > 0) {
            throw new RuntimeException("Cannot cleanup the table: " + table +
                    ". Remaining entity count is " + remainingEntityCount);
        }
    }

    @SuppressWarnings("BusyWait")   // allow Datastore to become consistent before reading.
    private void waitForConsistency() {
        if (!waitForConsistency) {
            log().info("Wait for consistency is not required.");
            return;
        }
        log().info("Waiting for data consistency to establish.");

        for (int awaitCycle = 0; awaitCycle < CONSISTENCY_AWAIT_ITERATIONS; awaitCycle++) {
            try {
                Thread.sleep(CONSISTENCY_AWAIT_TIME_MS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Deletes all records from the datastore.
     */
    /*package*/ void dropAllTables() {
        log().info("Dropping all tables");
        for (String kind : kindsCache) {
            dropTable(kind);
        }

        kindsCache.clear();
    }

    private static Logger log() {
        return LogSingleton.INSTANCE.value;
    }

    private enum LogSingleton {
        INSTANCE;
        @SuppressWarnings("NonSerializableFieldInSerializableClass")
        private final Logger value = LoggerFactory.getLogger(TestDatastoreWrapper.class);
    }
}