/*
 * Copyright 2018, TeamDev. All rights reserved.
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

import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.StructuredQuery;
import com.google.common.truth.IterableSubject;
import com.google.protobuf.Any;
import com.google.protobuf.FieldMask;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import io.spine.client.CompositeColumnFilter;
import io.spine.client.EntityFilters;
import io.spine.client.EntityId;
import io.spine.client.EntityIdFilter;
import io.spine.core.Version;
import io.spine.core.Versions;
import io.spine.server.entity.AbstractEntity;
import io.spine.server.entity.AbstractVersionableEntity;
import io.spine.server.entity.Entity;
import io.spine.server.entity.EntityRecord;
import io.spine.server.entity.LifecycleFlags;
import io.spine.server.entity.storage.Column;
import io.spine.server.entity.storage.EntityQuery;
import io.spine.server.entity.storage.EntityRecordWithColumns;
import io.spine.server.storage.RecordReadRequest;
import io.spine.server.storage.RecordStorage;
import io.spine.server.storage.RecordStorageTest;
import io.spine.server.storage.given.RecordStorageTestEnv.TestCounterEntity;
import io.spine.test.storage.Project;
import io.spine.test.storage.ProjectId;
import io.spine.test.storage.Task;
import io.spine.type.TypeUrl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.protobuf.util.Timestamps.toSeconds;
import static io.spine.base.Time.getCurrentTime;
import static io.spine.client.ColumnFilters.all;
import static io.spine.client.ColumnFilters.eq;
import static io.spine.json.Json.toCompactJson;
import static io.spine.protobuf.AnyPacker.pack;
import static io.spine.protobuf.AnyPacker.unpack;
import static io.spine.server.entity.storage.EntityQueries.from;
import static io.spine.server.entity.storage.EntityRecordWithColumns.create;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@DisplayName("DsRecordStorage should")
public class DsRecordStorageTest
        extends RecordStorageTest<DsRecordStorage<ProjectId>> {

    private static final String COLUMN_NAME_FOR_STORING = "columnName";
    private static final TestDatastoreStorageFactory datastoreFactory =
            TestDatastoreStorageFactory.getDefaultInstance();

    @SuppressWarnings("unchecked") // OK for tests.
    @Override
    protected DsRecordStorage<ProjectId> newStorage(Class<? extends Entity> entityClass) {
        Class<? extends Entity<ProjectId, ?>> cls =
                (Class<? extends Entity<ProjectId, ?>>) entityClass;
        return (DsRecordStorage<ProjectId>) datastoreFactory.createRecordStorage(cls);
    }

    @Override
    protected Class<? extends TestCounterEntity> getTestEntityClass() {
        return TestEntity.class;
    }

    @Override
    protected Message newState(ProjectId projectId) {
        Project project = Project.newBuilder()
                                 .setId(projectId)
                                 .setName("Some test name")
                                 .addTask(Task.getDefaultInstance())
                                 .setStatus(Project.Status.CREATED)
                                 .build();
        return project;
    }

    private EntityRecordWithColumns newRecordWithColumns(RecordStorage<ProjectId> storage) {
        EntityRecord record = newStorageRecord();
        TestConstCounterEntity entity = new TestConstCounterEntity(newId());
        EntityRecordWithColumns recordWithColumns = create(record, entity, storage);
        return recordWithColumns;
    }

    @BeforeEach
    void setUp() {
        datastoreFactory.setUp();
    }

    @AfterEach
    void tearDown() {
        datastoreFactory.tearDown();
    }

    @SuppressWarnings("DuplicateStringLiteralInspection") // OK for tests.
    @Test
    @DisplayName("provide access to DatastoreWrapper for extensibility")
    void testAccessDatastoreWrapper() {
        DsRecordStorage<ProjectId> storage = getStorage();
        DatastoreWrapper datastore = storage.getDatastore();
        assertNotNull(datastore);
    }

    @Test
    @DisplayName("provide access to TypeUrl for extensibility")
    void testAccessTypeUrl() {
        DsRecordStorage<ProjectId> storage = getStorage();
        TypeUrl typeUrl = storage.getTypeUrl();
        assertNotNull(typeUrl);

        // According to the `TestConstCounterEntity` declaration.
        assertEquals(TypeUrl.of(Project.class), typeUrl);
    }

    @SuppressWarnings("OverlyLongMethod")
    // A complicated test case verifying right Datastore behavior on
    // a low level of DatastoreWrapper and Datastore Entity.
    // Additionally checks the standard predefined Datastore Column Types
    @Test
    @DisplayName("persist entity columns beside the corresponding record")
    void testPersistColumns() {
        String counter = "counter";
        String bigCounter = "bigCounter";
        String counterEven = "counterEven";
        String counterVersion = "counterVersion";
        String creationTime = TestConstCounterEntity.CREATED_COLUMN_NAME;
        String counterState = "counterState";
        String version = "version";
        String archived = "archived";
        String deleted = "deleted";

        ProjectId id = newId();
        Project state = (Project) newState(id);
        Version versionValue = Versions.newVersion(5, getCurrentTime());
        TestConstCounterEntity entity = new TestConstCounterEntity(id);
        entity.injectState(state, versionValue);
        EntityRecord record = EntityRecord.newBuilder()
                                          .setState(pack(state))
                                          .setEntityId(pack(id))
                                          .setVersion(versionValue)
                                          .build();
        DsRecordStorage<ProjectId> storage = newStorage(TestConstCounterEntity.class);
        EntityRecordWithColumns recordWithColumns = create(record, entity, storage);
        Collection<String> columns = recordWithColumns.getColumnNames();
        assertNotNull(columns);

        IterableSubject assertColumns = assertThat(columns);

        // Custom Columns
        assertColumns.contains(counter);
        assertColumns.contains(bigCounter);
        assertColumns.contains(counterEven);
        assertColumns.contains(counterVersion);
        assertColumns.contains(creationTime);
        assertColumns.contains(counterState);

        // Columns defined in superclasses
        assertColumns.contains(version);
        assertColumns.contains(archived);
        assertColumns.contains(deleted);

        // High level write operation
        storage.write(id, recordWithColumns);

        // Read Datastore Entity
        DatastoreWrapper datastore = storage.getDatastore();
        Key key = DsIdentifiers.keyFor(datastore,
                                       Kind.of(state),
                                       DsIdentifiers.ofEntityId(id));
        com.google.cloud.datastore.Entity datastoreEntity = datastore.read(key);

        // Check entity record
        TypeUrl recordType = TypeUrl.from(EntityRecord.getDescriptor());
        EntityRecord readRecord = Entities.entityToMessage(datastoreEntity, recordType);
        assertEquals(record, readRecord);

        // Check custom Columns
        assertEquals(entity.getCounter(), datastoreEntity.getLong(counter));
        assertEquals(entity.getBigCounter(), datastoreEntity.getLong(bigCounter));
        assertEquals(entity.getCounterVersion()
                           .getNumber(), datastoreEntity.getLong(counterVersion));

        com.google.cloud.Timestamp actualCreationTime =
                datastoreEntity.getTimestamp(creationTime);
        assertEquals(toSeconds(entity.getCreationTime()), actualCreationTime.getSeconds());
        assertEquals(entity.getCreationTime()
                           .getNanos(), actualCreationTime.getNanos());
        assertEquals(entity.isCounterEven(), datastoreEntity.getBoolean(counterEven));
        assertEquals(toCompactJson(entity.getCounterState()),
                     datastoreEntity.getString(counterState));

        // Check standard Columns
        assertEquals(entity.getVersion()
                           .getNumber(), datastoreEntity.getLong(version));
        assertEquals(entity.isArchived(), datastoreEntity.getBoolean(archived));
        assertEquals(entity.isDeleted(), datastoreEntity.getBoolean(deleted));
    }

    @Disabled("This test rarely passes on Travis CI due to eventual consistency.")
    @Test
    @DisplayName("pass big data speed test")
    void testBigData() {
        // Default bulk size is 500 records - the maximum records that could be written within
        // one write operation
        long maxReadTime = 1000;
        long maxWriteTime = 9500;

        DsRecordStorage<ProjectId> storage = newStorage(TestConstCounterEntity.class);

        BigDataTester.<ProjectId>newBuilder()
                .setEntryFactory(new BigDataTester.EntryFactory<ProjectId>() {
                    @Override
                    public ProjectId newId() {
                        return DsRecordStorageTest.this.newId();
                    }

                    @Override
                    public EntityRecordWithColumns newRecord() {
                        return DsRecordStorageTest.this.newRecordWithColumns(storage);
                    }
                })
                .setReadLimit(maxReadTime)
                .setWriteLimit(maxWriteTime)
                .build()
                .testBigDataOperations(storage);
    }

    @Test
    @DisplayName("write and read records with lifecycle flags")
    void testLifecycleFlags() {
        ProjectId id = newId();
        LifecycleFlags lifecycle = LifecycleFlags.newBuilder()
                                                 .setArchived(true)
                                                 .build();
        EntityRecord record = EntityRecord.newBuilder()
                                          .setState(pack(newState(id)))
                                          .setLifecycleFlags(lifecycle)
                                          .setEntityId(pack(id))
                                          .build();
        TestConstCounterEntity entity = new TestConstCounterEntity(id);
        entity.injectLifecycle(lifecycle);
        RecordStorage<ProjectId> storage = newStorage(TestConstCounterEntity.class);
        EntityRecordWithColumns recordWithColumns = create(record, entity, storage);
        storage.write(id, recordWithColumns);
        RecordReadRequest<ProjectId> request = new RecordReadRequest<>(id);
        Optional<EntityRecord> restoredRecordOptional = storage.read(request);
        assertTrue(restoredRecordOptional.isPresent());
        EntityRecord restoredRecord = restoredRecordOptional.get();
        // Includes Lifecycle flags comparison
        assertEquals(record, restoredRecord);
    }

    @Test
    @DisplayName("convert entity record to entity using column name for storing")
    void testUseColumnStoreName() {
        DsRecordStorage<ProjectId> storage = newStorage(EntityWithCustomColumnName.class);
        ProjectId id = newId();
        EntityRecord record = EntityRecord.newBuilder()
                                          .setState(pack(newState(id)))
                                          .build();
        Entity entity = new EntityWithCustomColumnName(id);
        EntityRecordWithColumns entityRecordWithColumns = create(record, entity, storage);
        com.google.cloud.datastore.Entity datastoreEntity =
                storage.entityRecordToEntity(id, entityRecordWithColumns);
        Set<String> propertiesName = datastoreEntity.getNames();
        assertTrue(propertiesName.contains(COLUMN_NAME_FOR_STORING));
    }

    @Test
    @DisplayName("query by IDs when possible")
    void testQueryByIDs() {
        SpyStorageFactory.injectWrapper(datastoreFactory.getDatastore());
        DatastoreStorageFactory storageFactory = new SpyStorageFactory();
        RecordStorage<ProjectId> storage =
                storageFactory.createRecordStorage(TestConstCounterEntity.class);
        int recordCount = 10;
        int targetEntityIndex = 7;
        List<TestConstCounterEntity> entities = new ArrayList<>(recordCount);
        for (int i = 0; i < recordCount; i++) {
            TestConstCounterEntity entity = new TestConstCounterEntity(newId());
            entities.add(entity);
            EntityRecord record = EntityRecord.newBuilder()
                                              .setState(pack(entity.getState()))
                                              .build();
            EntityRecordWithColumns withColumns = create(record, entity, storage);
            storage.write(entity.getId(), withColumns);
        }
        TestConstCounterEntity targetEntity = entities.get(targetEntityIndex);
        EntityId targetId = EntityId.newBuilder()
                                    .setId(pack(targetEntity.getId()))
                                    .build();
        Object columnTargetValue = targetEntity.getCreationTime();
        EntityIdFilter idFilter = EntityIdFilter.newBuilder()
                                                .addIds(targetId)
                                                .build();
        CompositeColumnFilter columnFilter =
                all(eq(TestConstCounterEntity.CREATED_COLUMN_NAME, columnTargetValue));
        EntityFilters entityFilters = EntityFilters.newBuilder()
                                                   .setIdFilter(idFilter)
                                                   .addFilter(columnFilter)
                                                   .build();
        EntityQuery<ProjectId> entityQuery = from(entityFilters, storage);
        Iterator<EntityRecord> readResult = storage.readAll(entityQuery,
                                                            FieldMask.getDefaultInstance());
        List<EntityRecord> resultList = newArrayList(readResult);
        assertEquals(1, resultList.size());
        assertEquals(targetEntity.getState(), unpack(resultList.get(0).getState()));

        DatastoreWrapper spy = storageFactory.getDatastore();
        verify(spy).read(anyIterable());
        verify(spy, never()).read(any(StructuredQuery.class));
    }

    /*
     * Test Entity types
     ************************/

    @SuppressWarnings("unused") // Reflective access
    public static class TestConstCounterEntity
            extends AbstractVersionableEntity<ProjectId, Project> {

        private static final String CREATED_COLUMN_NAME = "creationTime";
        private static final int COUNTER = 42;

        private final Timestamp creationTime;
        private LifecycleFlags lifecycleFlags;

        private TestConstCounterEntity(ProjectId id) {
            super(id);
            this.creationTime = getCurrentTime();
        }

        @Column
        public int getCounter() {
            return COUNTER;
        }

        @Column
        public long getBigCounter() {
            return getCounter();
        }

        @Column
        public boolean isCounterEven() {
            return true;
        }

        @Column
        public String getCounterName() {
            return getId().toString();
        }

        @Column
        public Version getCounterVersion() {
            return Version.newBuilder()
                          .setNumber(COUNTER)
                          .build();
        }

        @Column
        public Timestamp getCreationTime() {
            return creationTime;
        }

        @Column
        public Project getCounterState() {
            return getState();
        }

        @Override
        public LifecycleFlags getLifecycleFlags() {
            return lifecycleFlags == null ? super.getLifecycleFlags() : lifecycleFlags;
        }

        private void injectState(Project state, Version version) {
            updateState(state);
        }

        private void injectLifecycle(LifecycleFlags flags) {
            this.lifecycleFlags = flags;
        }
    }

    public static class TestEntity extends TestCounterEntity {

        protected TestEntity(ProjectId id) {
            super(id);
        }
    }

    public static class EntityWithCustomColumnName extends AbstractEntity<ProjectId, Any> {

        private EntityWithCustomColumnName(ProjectId id) {
            super(id);
        }

        @Column(name = COLUMN_NAME_FOR_STORING)
        public int getValue() {
            return 0;
        }
    }

    /**
     * A {@link TestDatastoreStorageFactory} which spies on its {@link DatastoreWrapper}.
     */
    private static class SpyStorageFactory extends TestDatastoreStorageFactory {

        private static DatastoreWrapper spyWrapper = null;

        private static void injectWrapper(DatastoreWrapper wrapper) {
            spyWrapper = spy(wrapper);
        }

        private SpyStorageFactory() {
            super(spyWrapper.getDatastore());
        }

        @Override
        protected DatastoreWrapper createDatastoreWrapper(Datastore datastore) {
            return spyWrapper;
        }
    }
}
