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

import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.Key;
import com.google.protobuf.Timestamp;
import io.spine.core.Version;
import io.spine.core.Versions;
import io.spine.environment.Tests;
import io.spine.server.ServerEnvironment;
import io.spine.server.storage.MessageRecordSpec;
import io.spine.server.storage.RecordStorageDelegateTest;
import io.spine.server.storage.RecordWithColumns;
import io.spine.server.storage.datastore.BigDataTester;
import io.spine.server.storage.datastore.DatastoreWrapper;
import io.spine.server.storage.datastore.Kind;
import io.spine.server.storage.given.StgProjectStorage;
import io.spine.test.storage.StgProject;
import io.spine.test.storage.StgProjectId;
import io.spine.testing.SlowTest;
import io.spine.testing.server.storage.datastore.TestDatastoreStorageFactory;
import io.spine.type.TypeUrl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.google.common.truth.Truth8.assertThat;
import static io.spine.base.Time.currentTime;
import static io.spine.server.storage.given.StgColumn.due_date;
import static io.spine.server.storage.given.StgColumn.project_version;
import static io.spine.server.storage.given.StgColumn.status;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("`DsRecordStorage` should")
final class DsRecordStorageTest extends RecordStorageDelegateTest {

    private static final TestDatastoreStorageFactory datastoreFactory =
            TestDatastoreStorageFactory.local();

    @BeforeAll
    static void configureStorageFactory() {
        ServerEnvironment.when(Tests.class)
                         .useStorageFactory((env) -> datastoreFactory);
    }

    @BeforeEach
    void setUp() {
        datastoreFactory.setUp();
    }

    @AfterEach
    void tearDown() {
        datastoreFactory.tearDown();
    }

    @SlowTest
    @Test
    @DisplayName("pass big data speed test")
    void testBigData() {
        // Default bulk size is 500 records - the maximum records that could be written within
        // one write operation
        long maxReadTime = 1000;
        long maxWriteTime = 9500;

        StgProjectStorage storage = newStorage();

        BigDataTester.<StgProjectId, StgProject>newBuilder()
                     .setEntryFactory(new BigDataTester.EntryFactory<StgProjectId, StgProject>() {
                         @Override
                         public StgProjectId newId() {
                             return DsRecordStorageTest.this.newId();
                         }

                         @Override
                         public StgProject newRecord(StgProjectId id) {
                             return DsRecordStorageTest.this.newStorageRecord(id);
                         }
                     })
                     .setReadLimit(maxReadTime)
                     .setWriteLimit(maxWriteTime)
                     .build()
                     .testBigDataOperations(storage);
    }

    @Test
    @DisplayName("persist entity columns beside the corresponding record")
    @SuppressWarnings("ProtoTimestampGetSecondsGetNano") /* Compares points in time.*/
    void testPersistColumns() {
        StgProjectId id = newId();
        StgProject project = newStorageRecord(id);
        Version expectedVersion = Versions.newVersion(42, currentTime());
        Timestamp expectedDueDate = currentTime();
        StgProject.Status expectedStatus = StgProject.Status.STARTED;
        project = project
                .toBuilder()
                .setProjectVersion(expectedVersion)
                .setDueDate(expectedDueDate)
                .setStatus(expectedStatus)
                .vBuild();
        MessageRecordSpec<StgProjectId, StgProject> spec =
                new MessageRecordSpec<>(StgProjectId.class, StgProject.class, StgProject::getId);
        RecordWithColumns<StgProjectId, StgProject> record =
                RecordWithColumns.create(project, spec);
        storage().write(id, project);

        // Read Datastore Entity
        DatastoreWrapper datastore = datastoreFactory.newDatastoreWrapper(
                storage().isMultitenant());
        Key key = datastore.keyFor(Kind.of(StgProject.class), RecordId.ofEntityId(id));
        Optional<Entity> readResult = datastore.read(key);
        assertThat(readResult).isPresent();
        Entity datastoreEntity = readResult.get();

        // Check entity record
        TypeUrl recordType = TypeUrl.from(StgProject.getDescriptor());
        StgProject actualProject = Entities.toMessage(datastoreEntity, recordType);
        assertEquals(project, actualProject);

        // Check custom Columns
        assertEquals(expectedStatus.name(),
                     datastoreEntity.getString(status.name()
                                                     .value()));
        assertEquals(expectedVersion.getNumber(),
                     datastoreEntity.getLong(project_version.name()
                                                            .value()));
        com.google.cloud.Timestamp actualDueDate =
                datastoreEntity.getTimestamp(due_date.name()
                                                     .value());
        assertEquals(expectedDueDate.getSeconds(), actualDueDate.getSeconds());
        assertEquals(expectedDueDate.getNanos(), actualDueDate.getNanos());
    }
}
