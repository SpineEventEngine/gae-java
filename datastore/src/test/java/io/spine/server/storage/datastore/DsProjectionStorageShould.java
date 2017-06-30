/*
 * Copyright 2017, TeamDev Ltd. All rights reserved.
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

import io.spine.core.Version;
import io.spine.protobuf.AnyPacker;
import io.spine.server.entity.Entity;
import io.spine.server.entity.EntityRecord;
import io.spine.server.projection.Projection;
import io.spine.server.projection.ProjectionStorage;
import io.spine.server.projection.ProjectionStorageShould;
import io.spine.test.storage.Project;
import io.spine.test.storage.ProjectVBuilder;
import io.spine.testdata.Sample;
import org.junit.After;
import org.junit.Test;

import static io.spine.time.Time.getCurrentTime;
import static org.junit.Assert.assertNotNull;

/**
 * @author Mikhail Mikhaylov
 */
public class DsProjectionStorageShould extends ProjectionStorageShould {

    private static final TestDatastoreStorageFactory datastoreFactory =
            TestDatastoreStorageFactory.getDefaultInstance();

    @Override
    protected Class<? extends TestCounterEntity> getTestEntityClass() {
        return TestEntity.class;
    }

    @SuppressWarnings({"MagicNumber", "MethodDoesntCallSuperMethod"})
    @Override
    protected EntityRecord newStorageRecord() {
        return EntityRecord.newBuilder()
                .setState(
                        AnyPacker.pack(Sample.messageOfType(Project.class)))
                .setVersion(Version.newBuilder().setNumber(42).setTimestamp(getCurrentTime()))
                .build();
    }

    @After
    public void tearDownTest() {
        datastoreFactory.clear();
    }

    @SuppressWarnings("unchecked") // Required for test purposes.
    @Override
    protected ProjectionStorage<io.spine.test.storage.ProjectId> getStorage(Class<? extends Entity> cls) {
        final Class<? extends Projection<io.spine.test.storage.ProjectId, ?, ?>> projectionClass =
                (Class<? extends Projection<io.spine.test.storage.ProjectId, ?, ?>>) cls;
        final ProjectionStorage<io.spine.test.storage.ProjectId> result =
                datastoreFactory.createProjectionStorage(projectionClass);
        return result;
    }

    @Test
    public void provide_access_to_PropertyStorage_for_extensibility() {
        final DsProjectionStorage<io.spine.test.storage.ProjectId> storage = (DsProjectionStorage<io.spine.test.storage.ProjectId>) getStorage(TestProjection.class);
        final DsPropertyStorage propertyStorage = storage.propertyStorage();
        assertNotNull(propertyStorage);
    }

    private static class TestProjection extends Projection<io.spine.test.storage.ProjectId,
                                                           Project,
                                                           ProjectVBuilder> {
        private TestProjection(io.spine.test.storage.ProjectId id) {
            super(id);
        }
    }

    public static class TestEntity extends TestCounterEntity<io.spine.test.storage.ProjectId> {

        protected TestEntity(io.spine.test.storage.ProjectId id) {
            super(id);
        }
    }
}
