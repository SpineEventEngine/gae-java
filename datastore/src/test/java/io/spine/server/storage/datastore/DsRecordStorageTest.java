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
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import io.spine.client.CompositeColumnFilter;
import io.spine.client.EntityFilters;
import io.spine.client.EntityId;
import io.spine.client.EntityIdFilter;
import io.spine.core.Version;
import io.spine.core.Versions;
import io.spine.server.entity.Entity;
import io.spine.server.entity.EntityRecord;
import io.spine.server.entity.LifecycleFlags;
import io.spine.server.entity.storage.EntityQuery;
import io.spine.server.entity.storage.EntityRecordWithColumns;
import io.spine.server.storage.RecordReadRequest;
import io.spine.server.storage.RecordStorage;
import io.spine.server.storage.RecordStorageTest;
import io.spine.server.storage.datastore.given.DsRecordStorageTestEnv.CollegeEntity;
import io.spine.server.storage.datastore.given.DsRecordStorageTestEnv.EntityWithCustomColumnName;
import io.spine.server.storage.datastore.given.DsRecordStorageTestEnv.TestConstCounterEntity;
import io.spine.server.storage.datastore.given.DsRecordStorageTestEnv.TestEntity;
import io.spine.server.storage.given.RecordStorageTestEnv.TestCounterEntity;
import io.spine.test.datastore.CollegeId;
import io.spine.test.storage.Project;
import io.spine.test.storage.ProjectId;
import io.spine.test.storage.Task;
import io.spine.type.TypeUrl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Lists.reverse;
import static com.google.protobuf.util.Timestamps.toSeconds;
import static io.spine.base.Time.getCurrentTime;
import static io.spine.client.ColumnFilters.all;
import static io.spine.client.ColumnFilters.eq;
import static io.spine.json.Json.toCompactJson;
import static io.spine.protobuf.AnyPacker.pack;
import static io.spine.protobuf.AnyPacker.unpack;
import static io.spine.server.entity.storage.EntityQueries.from;
import static io.spine.server.entity.storage.EntityRecordWithColumns.create;
import static io.spine.server.storage.datastore.given.DsRecordStorageTestEnv.COLUMN_NAME_FOR_STORING;
import static io.spine.server.storage.datastore.given.DsRecordStorageTestEnv.CollegeEntity.Columns.ADMISSION_DEADLINE;
import static io.spine.server.storage.datastore.given.DsRecordStorageTestEnv.CollegeEntity.Columns.CREATED;
import static io.spine.server.storage.datastore.given.DsRecordStorageTestEnv.CollegeEntity.Columns.NAME;
import static io.spine.server.storage.datastore.given.DsRecordStorageTestEnv.CollegeEntity.Columns.PASSING_GRADE;
import static io.spine.server.storage.datastore.given.DsRecordStorageTestEnv.CollegeEntity.Columns.STATE_SPONSORED;
import static io.spine.server.storage.datastore.given.DsRecordStorageTestEnv.CollegeEntity.Columns.STUDENT_COUNT;
import static io.spine.server.storage.datastore.given.DsRecordStorageTestEnv.TestConstCounterEntity.CREATED_COLUMN;
import static io.spine.server.storage.datastore.given.DsRecordStorageTestEnv.UNORDERED_COLLEGE_NAMES;
import static io.spine.server.storage.datastore.given.DsRecordStorageTestEnv.ascendingBy;
import static io.spine.server.storage.datastore.given.DsRecordStorageTestEnv.createAndStoreEntities;
import static io.spine.server.storage.datastore.given.DsRecordStorageTestEnv.datastoreFactory;
import static io.spine.server.storage.datastore.given.DsRecordStorageTestEnv.descendingBy;
import static io.spine.server.storage.datastore.given.DsRecordStorageTestEnv.emptyFieldMask;
import static io.spine.server.storage.datastore.given.DsRecordStorageTestEnv.emptyOrderBy;
import static io.spine.server.storage.datastore.given.DsRecordStorageTestEnv.emptyPagination;
import static io.spine.server.storage.datastore.given.DsRecordStorageTestEnv.newEntityFilters;
import static io.spine.server.storage.datastore.given.DsRecordStorageTestEnv.newEntityId;
import static io.spine.server.storage.datastore.given.DsRecordStorageTestEnv.newEntityIds;
import static io.spine.server.storage.datastore.given.DsRecordStorageTestEnv.newEntityRecord;
import static io.spine.server.storage.datastore.given.DsRecordStorageTestEnv.newIdFilter;
import static io.spine.server.storage.datastore.given.DsRecordStorageTestEnv.pagination;
import static io.spine.server.storage.datastore.given.DsRecordStorageTestEnv.recordIds;
import static io.spine.server.storage.datastore.given.DsRecordStorageTestEnv.sortedIds;
import static io.spine.testing.Verify.assertContains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link DsRecordStorage}.
 *
 * @author Dmytro Dashenkov
 */
@DisplayName("DsRecordStorage should")
class DsRecordStorageTest extends RecordStorageTest<DsRecordStorage<ProjectId>> {

    private final TestDatastoreStorageFactory datastoreFactory = datastoreFactory();

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
        Entity<ProjectId, Project> entity = new TestConstCounterEntity(newId());
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
        String creationTime = CREATED_COLUMN;
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

        // Custom Columns
        assertContains(counter, columns);
        assertContains(bigCounter, columns);
        assertContains(counterEven, columns);
        assertContains(counterVersion, columns);
        assertContains(creationTime, columns);
        assertContains(counterState, columns);

        // Columns defined in superclasses
        assertContains(version, columns);
        assertContains(archived, columns);
        assertContains(deleted, columns);

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
        EntityRecord record = newEntityRecord(id, newState(id));
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
        // Initialize test storage.
        SpyStorageFactory.injectWrapper(datastoreFactory().getDatastore());
        DatastoreStorageFactory storageFactory = new SpyStorageFactory();
        RecordStorage<CollegeId> storage = storageFactory.createRecordStorage(CollegeEntity.class);

        // Create 10 entities and pick one for tests.
        int recordCount = 10;
        int targetEntityIndex = 7;
        List<CollegeEntity> entities = createAndStoreEntities(storage, recordCount);
        CollegeEntity targetEntity = entities.get(targetEntityIndex);

        // Create ID filter.
        EntityId targetId = newEntityId(targetEntity);
        EntityIdFilter idFilter = newIdFilter(targetId);

        // Create column filter.
        Timestamp targetColumnValue = targetEntity.getCreationTime();
        CompositeColumnFilter columnFilter = all(eq(CREATED.columnName(), targetColumnValue));

        // Compose Query filters.
        EntityFilters entityFilters = newEntityFilters(idFilter, columnFilter);

        // Compose Query.
        EntityQuery<CollegeId> entityQuery =
                from(entityFilters, emptyOrderBy(), emptyPagination(), storage);

        // Execute Query.
        Iterator<EntityRecord> readResult = storage.readAll(entityQuery, emptyFieldMask());

        // Check the query results.
        List<EntityRecord> resultList = newArrayList(readResult);
        assertEquals(1, resultList.size());

        // Check the record state.
        EntityRecord record = resultList.get(0);
        assertEquals(targetEntity.getState(), unpack(record.getState()));

        // Check Datastore reads are performed by keys but not using a structured query.
        DatastoreWrapper spy = storageFactory.getDatastore();
        verify(spy).read(anyIterable());
        //noinspection unchecked OK for a generic class assignment in tests.
        verify(spy, never()).read(any(StructuredQuery.class));
    }

    @Test
    @DisplayName("query by IDs in specified order by string field")
    void testQueryByIDsWithDescendingOrder() {
        // Initialize test storage.
        SpyStorageFactory.injectWrapper(datastoreFactory().getDatastore());
        DatastoreStorageFactory storageFactory = new SpyStorageFactory();
        RecordStorage<CollegeId> storage = storageFactory.createRecordStorage(CollegeEntity.class);

        // Create 10 entities and pick one for tests.
        int recordCount = UNORDERED_COLLEGE_NAMES.size();
        List<CollegeEntity> entities = createAndStoreEntities(storage, UNORDERED_COLLEGE_NAMES);

        // Create ID filter.
        List<EntityId> targetIds = newEntityIds(entities);
        EntityIdFilter idFilter = newIdFilter(targetIds);

        // Compose Query filters.
        EntityFilters entityFilters = newEntityFilters(idFilter);

        // Compose Query.
        EntityQuery<CollegeId> entityQuery = from(entityFilters,
                                                  descendingBy(NAME.columnName()),
                                                  emptyPagination(),
                                                  storage);

        // Execute Query.
        Iterator<EntityRecord> readResult = storage.readAll(entityQuery, emptyFieldMask());

        // Check the query results.
        List<EntityRecord> resultList = newArrayList(readResult);
        assertEquals(recordCount, resultList.size());

        // Check the entities were ordered.
        List<CollegeId> expectedResults = reverse(sortedIds(entities, CollegeEntity::getName));
        List<CollegeId> actualResults = recordIds(resultList);
        assertEquals(expectedResults, actualResults);

        // Check Datastore reads are performed by keys but not using a structured query.
        DatastoreWrapper spy = storageFactory.getDatastore();
        verify(spy).read(anyIterable());
        //noinspection unchecked OK for a generic class assignment in tests.
        verify(spy, never()).read(any(StructuredQuery.class));
    }

    @Test
    @DisplayName("query by IDs in specified order by string field")
    void testQueryByIDsWithOrderByString() {
        // Initialize test storage.
        SpyStorageFactory.injectWrapper(datastoreFactory().getDatastore());
        DatastoreStorageFactory storageFactory = new SpyStorageFactory();
        RecordStorage<CollegeId> storage = storageFactory.createRecordStorage(CollegeEntity.class);

        // Create 10 entities and pick one for tests.
        int recordCount = UNORDERED_COLLEGE_NAMES.size();
        List<CollegeEntity> entities = createAndStoreEntities(storage, UNORDERED_COLLEGE_NAMES);

        // Create ID filter.
        List<EntityId> targetIds = newEntityIds(entities);
        EntityIdFilter idFilter = newIdFilter(targetIds);

        // Compose Query filters.
        EntityFilters entityFilters = newEntityFilters(idFilter);

        // Compose Query.
        EntityQuery<CollegeId> entityQuery = from(entityFilters,
                                                  ascendingBy(NAME.columnName()),
                                                  emptyPagination(),
                                                  storage);

        // Execute Query.
        Iterator<EntityRecord> readResult = storage.readAll(entityQuery, emptyFieldMask());

        // Check the query results.
        List<EntityRecord> resultList = newArrayList(readResult);
        assertEquals(recordCount, resultList.size());

        // Check the entities were ordered.
        List<CollegeId> expectedResults = sortedIds(entities, CollegeEntity::getName);
        List<CollegeId> actualResults = recordIds(resultList);
        assertEquals(expectedResults, actualResults);

        // Check Datastore reads are performed by keys but not using a structured query.
        DatastoreWrapper spy = storageFactory.getDatastore();
        verify(spy).read(anyIterable());
        //noinspection unchecked OK for a generic class assignment in tests.
        verify(spy, never()).read(any(StructuredQuery.class));
    }

    @Test
    @DisplayName("query by IDs in specified order by double field")
    void testQueryByIDsWithOrderByDouble() {
        // Initialize test storage.
        SpyStorageFactory.injectWrapper(datastoreFactory().getDatastore());
        DatastoreStorageFactory storageFactory = new SpyStorageFactory();
        RecordStorage<CollegeId> storage = storageFactory.createRecordStorage(CollegeEntity.class);

        // Create 10 entities and pick one for tests.
        int recordCount = 20;
        List<CollegeEntity> entities = createAndStoreEntities(storage, recordCount);

        // Create ID filter.
        List<EntityId> targetIds = newEntityIds(entities);
        EntityIdFilter idFilter = newIdFilter(targetIds);

        // Compose Query filters.
        EntityFilters entityFilters = newEntityFilters(idFilter);

        // Compose Query.
        EntityQuery<CollegeId> entityQuery = from(entityFilters,
                                                  ascendingBy(PASSING_GRADE.columnName()),
                                                  emptyPagination(),
                                                  storage);

        // Execute Query.
        Iterator<EntityRecord> readResult = storage.readAll(entityQuery, emptyFieldMask());

        // Check the query results.
        List<EntityRecord> resultList = newArrayList(readResult);
        assertEquals(recordCount, resultList.size());

        // Check the entities were ordered.
        List<CollegeId> expectedResults = sortedIds(entities, CollegeEntity::getPassingGrade);
        List<CollegeId> actualResults = recordIds(resultList);
        assertEquals(expectedResults, actualResults);

        // Check Datastore reads are performed by keys but not using a structured query.
        DatastoreWrapper spy = storageFactory.getDatastore();
        verify(spy).read(anyIterable());
        //noinspection unchecked OK for a generic class assignment in tests.
        verify(spy, never()).read(any(StructuredQuery.class));
    }

    @Test
    @DisplayName("query by IDs in specified order by timestamp field")
    void testQueryByIDsWithOrderByTimestamp() {
        // Initialize test storage.
        SpyStorageFactory.injectWrapper(datastoreFactory().getDatastore());
        DatastoreStorageFactory storageFactory = new SpyStorageFactory();
        RecordStorage<CollegeId> storage = storageFactory.createRecordStorage(CollegeEntity.class);

        // Create 10 entities and pick one for tests.
        int recordCount = 20;
        List<CollegeEntity> entities = createAndStoreEntities(storage, recordCount);

        // Create ID filter.
        List<EntityId> targetIds = newEntityIds(entities);
        EntityIdFilter idFilter = newIdFilter(targetIds);

        // Compose Query filters.
        EntityFilters entityFilters = newEntityFilters(idFilter);

        // Compose Query.
        EntityQuery<CollegeId> entityQuery = from(entityFilters,
                                                  ascendingBy(ADMISSION_DEADLINE.columnName()),
                                                  emptyPagination(),
                                                  storage);

        // Execute Query.
        Iterator<EntityRecord> readResult = storage.readAll(entityQuery, emptyFieldMask());

        // Check the query results.
        List<EntityRecord> resultList = newArrayList(readResult);
        assertEquals(recordCount, resultList.size());

        // Check the entities were ordered.
        List<CollegeId> expectedResults = sortedIds(entities,
                                                    entity -> entity.getAdmissionDeadline()
                                                                    .getSeconds());
        List<CollegeId> actualResults = recordIds(resultList);
        assertEquals(expectedResults, actualResults);

        // Check Datastore reads are performed by keys but not using a structured query.
        DatastoreWrapper spy = storageFactory.getDatastore();
        verify(spy).read(anyIterable());
        //noinspection unchecked OK for a generic class assignment in tests.
        verify(spy, never()).read(any(StructuredQuery.class));
    }

    @Test
    @DisplayName("query by IDs in specified order by integer")
    void testQueryByIDsWithOrderByInt() {
        // Initialize test storage.
        SpyStorageFactory.injectWrapper(datastoreFactory().getDatastore());
        DatastoreStorageFactory storageFactory = new SpyStorageFactory();
        RecordStorage<CollegeId> storage = storageFactory.createRecordStorage(CollegeEntity.class);

        // Create 10 entities and pick one for tests.
        int recordCount = 20;
        List<CollegeEntity> entities = createAndStoreEntities(storage, recordCount);

        // Create ID filter.
        List<EntityId> targetIds = newEntityIds(entities);
        EntityIdFilter idFilter = newIdFilter(targetIds);

        // Compose Query filters.
        EntityFilters entityFilters = newEntityFilters(idFilter);

        // Compose Query.
        EntityQuery<CollegeId> entityQuery = from(entityFilters,
                                                  ascendingBy(STUDENT_COUNT.columnName()),
                                                  emptyPagination(),
                                                  storage);

        // Execute Query.
        Iterator<EntityRecord> readResult = storage.readAll(entityQuery, emptyFieldMask());

        // Check the query results.
        List<EntityRecord> resultList = newArrayList(readResult);
        assertEquals(recordCount, resultList.size());

        // Check the entities were ordered.
        List<CollegeId> expectedResults = sortedIds(entities, CollegeEntity::getStudentCount);
        List<CollegeId> actualResults = recordIds(resultList);
        assertEquals(expectedResults, actualResults);

        // Check Datastore reads are performed by keys but not using a structured query.
        DatastoreWrapper spy = storageFactory.getDatastore();
        verify(spy).read(anyIterable());
        //noinspection unchecked OK for a generic class assignment in tests.
        verify(spy, never()).read(any(StructuredQuery.class));
    }

    @Test
    @DisplayName("query by IDs in specified order by boolean")
    void testQueryByIDsWithOrderByBoolean() {
        // Initialize test storage.
        SpyStorageFactory.injectWrapper(datastoreFactory().getDatastore());
        DatastoreStorageFactory storageFactory = new SpyStorageFactory();
        RecordStorage<CollegeId> storage = storageFactory.createRecordStorage(CollegeEntity.class);

        // Create 10 entities and pick one for tests.
        int recordCount = 20;
        List<CollegeEntity> entities = createAndStoreEntities(storage, recordCount);

        // Create ID filter.
        List<EntityId> targetIds = newEntityIds(entities);
        EntityIdFilter idFilter = newIdFilter(targetIds);

        // Compose Query filters.
        EntityFilters entityFilters = newEntityFilters(idFilter);

        // Compose Query.
        EntityQuery<CollegeId> entityQuery = from(entityFilters,
                                                  ascendingBy(STATE_SPONSORED.columnName()),
                                                  emptyPagination(),
                                                  storage);

        // Execute Query.
        Iterator<EntityRecord> readResult = storage.readAll(entityQuery, emptyFieldMask());

        // Check the query results.
        List<EntityRecord> resultList = newArrayList(readResult);
        assertEquals(recordCount, resultList.size());

        // Check the entities were ordered.
        List<CollegeId> expectedResults = sortedIds(entities, CollegeEntity::isStateSponsored);
        List<CollegeId> actualResults = recordIds(resultList);
        assertEquals(expectedResults, actualResults);

        // Check Datastore reads are performed by keys but not using a structured query.
        DatastoreWrapper spy = storageFactory.getDatastore();
        verify(spy).read(anyIterable());
        //noinspection unchecked OK for a generic class assignment in tests.
        verify(spy, never()).read(any(StructuredQuery.class));
    }

    @Test
    @DisplayName("query by IDs a specified number of entities")
    void testQueryByIDsWithLimit() {
        // Initialize test storage.
        SpyStorageFactory.injectWrapper(datastoreFactory().getDatastore());
        DatastoreStorageFactory storageFactory = new SpyStorageFactory();
        RecordStorage<CollegeId> storage = storageFactory.createRecordStorage(CollegeEntity.class);

        // Create 10 entities and pick one for tests.
        int expectedRecordCount = 4;
        List<CollegeEntity> entities = createAndStoreEntities(storage, UNORDERED_COLLEGE_NAMES);

        // Create ID filter.
        List<EntityId> targetIds = newEntityIds(entities);
        EntityIdFilter idFilter = newIdFilter(targetIds);

        // Compose Query filters.
        EntityFilters entityFilters = newEntityFilters(idFilter);

        // Compose Query.
        EntityQuery<CollegeId> entityQuery = from(entityFilters,
                                                  ascendingBy(NAME.columnName()),
                                                  pagination(expectedRecordCount),
                                                  storage);

        // Execute Query.
        Iterator<EntityRecord> readResult = storage.readAll(entityQuery, emptyFieldMask());

        // Check the query results.
        List<EntityRecord> resultList = newArrayList(readResult);
        assertEquals(expectedRecordCount, resultList.size());

        // Check the entities were ordered.
        List<CollegeId> sortedIds = sortedIds(entities, CollegeEntity::getName);
        List<CollegeId> expectedResults = sortedIds.subList(0, expectedRecordCount);
        List<CollegeId> actualResults = recordIds(resultList);
        assertEquals(expectedResults, actualResults);

        // Check Datastore reads are performed by keys but not using a structured query.
        DatastoreWrapper spy = storageFactory.getDatastore();
        verify(spy).read(anyIterable());
        //noinspection unchecked OK for a generic class assignment in tests.
        verify(spy, never()).read(any(StructuredQuery.class));
    }

    /*
     * Test Entity types
     ************************/

    /**
     * A {@link TestDatastoreStorageFactory} which spies on its {@link DatastoreWrapper}.
     *
     * This class is not moved to the
     * {@linkplain io.spine.server.storage.datastore.given.DsRecordStorageTestEnv test environment}
     * because it uses package-private method of {@link DatastoreWrapper}.
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
