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

package org.spine3.server.storage.datastore;

import com.google.cloud.datastore.Datastore;
import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Message;
import org.spine3.server.aggregate.Aggregate;
import org.spine3.server.aggregate.AggregateStorage;
import org.spine3.server.entity.Entity;
import org.spine3.server.entity.storage.ColumnTypeRegistry;
import org.spine3.server.projection.ProjectionStorage;
import org.spine3.server.stand.StandStorage;
import org.spine3.server.storage.RecordStorage;
import org.spine3.server.storage.StorageFactory;
import org.spine3.server.storage.datastore.type.DatastoreColumnType;
import org.spine3.server.storage.datastore.type.DatastoreTypeRegistryFactory;
import org.spine3.type.TypeUrl;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.spine3.server.entity.Entity.GenericParameter.ID;
import static org.spine3.server.entity.Entity.GenericParameter.STATE;
import static org.spine3.server.reflect.Classes.getGenericParameterType;

/**
 * Creates storages based on GAE {@link Datastore}.
 *
 * @author Alexander Litus
 * @author Mikhail Mikhaylov
 */
@SuppressWarnings("WeakerAccess") // Part of API
public class DatastoreStorageFactory implements StorageFactory {

    private static final int ENTITY_MESSAGE_TYPE_PARAMETER_INDEX = 1;

    private DatastoreWrapper datastore;
    private final boolean multitenant;
    private final ColumnTypeRegistry<? extends DatastoreColumnType<?, ?>> typeRegistry;

    private DatastoreStorageFactory(Builder builder) {
        this(builder.datastore, builder.multitenant, builder.typeRegistry);
    }

    @VisibleForTesting
    @SuppressWarnings({"OverridableMethodCallDuringObjectConstruction", "OverriddenMethodCallDuringObjectConstruction"})
    protected DatastoreStorageFactory(Datastore datastore,
                                      boolean multitenant,
                                      ColumnTypeRegistry<? extends DatastoreColumnType<?, ?>> typeRegistry) {
        this.multitenant = multitenant;
        this.typeRegistry = typeRegistry;
        initDatastoreWrapper(datastore);
    }

    protected DatastoreStorageFactory(DatastoreWrapper datastore,
                                      boolean multitenant,
                                      ColumnTypeRegistry<? extends DatastoreColumnType<?, ?>> typeRegistry) {
        this.datastore = datastore;
        this.multitenant = multitenant;
        this.typeRegistry = typeRegistry;
    }

    private void initDatastoreWrapper(Datastore datastore) {
        checkState(this.getDatastore() == null, "Datastore is already initialized");
        final DatastoreWrapper wrapped = DatastoreWrapper.wrap(datastore);
        this.setDatastore(wrapped);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isMultitenant() {
        return multitenant;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ColumnTypeRegistry getTypeRegistry() {
        return typeRegistry;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StorageFactory toSingleTenant() {
        return isMultitenant()
                ? new DatastoreStorageFactory(getDatastore(), false, typeRegistry)
                : this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StandStorage createStandStorage() {
        final DsStandStorageDelegate recordStorage = new DsStandStorageDelegate(datastore, multitenant);
        final DsStandStorage result = new DsStandStorage(recordStorage, multitenant);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <I> ProjectionStorage<I> createProjectionStorage(Class<? extends Entity<I, ?>> aClass) {
        final DsRecordStorage<I> recordStorage = (DsRecordStorage<I>) createRecordStorage(aClass);
        final DsPropertyStorage propertyStorage = createPropertyStorage();
        final DsProjectionStorage<I> result = new DsProjectionStorage<>(recordStorage,
                                                                        propertyStorage,
                                                                        aClass,
                                                                        multitenant);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <I> RecordStorage<I> createRecordStorage(Class<? extends Entity<I, ?>> entityClass) {
        final Class<Message> messageClass = getGenericParameterType(entityClass, ENTITY_MESSAGE_TYPE_PARAMETER_INDEX);
        final TypeUrl typeUrl = TypeUrl.of(messageClass);
        final Class<I> idClass = getGenericParameterType(entityClass, ID.getIndex());
        final Descriptor descriptor = (Descriptor) typeUrl.getDescriptor();
        final DsRecordStorage<I> result = new DsRecordStorage<>(descriptor,
                                                                getDatastore(),
                                                                multitenant,
                                                                typeRegistry,
                                                                idClass);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <I> AggregateStorage<I> createAggregateStorage(Class<? extends Aggregate<I, ?, ?>> entityClass) {
        checkNotNull(entityClass);
        final DsPropertyStorage propertyStorage = createPropertyStorage();
        final Class<I> idClass = getGenericParameterType(entityClass, ID.getIndex());
        final Class<? extends Message> stateClass = getGenericParameterType(entityClass, STATE.getIndex());
        final DsAggregateStorage<I> result = new DsAggregateStorage<>(getDatastore(),
                                                                      propertyStorage,
                                                                      multitenant,
                                                                      idClass,
                                                                      stateClass);
        return result;
    }

    protected DsPropertyStorage createPropertyStorage() {
        final DsPropertyStorage propertyStorage = DsPropertyStorage.newInstance(getDatastore());
        return propertyStorage;
    }

    /**
     * Performs no action.
     */
    @Override
    public void close() throws Exception {
        // NOP
    }

    protected DatastoreWrapper getDatastore() {
        return datastore;
    }

    protected void setDatastore(DatastoreWrapper datastore) {
        this.datastore = datastore;
    }

    /**
     * Creates new instance of {@link Builder}.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * A builder for the {@code DatastoreStorageFactory}.
     */
    public static class Builder {

        private Datastore datastore;
        private boolean multitenant;
        private ColumnTypeRegistry<? extends DatastoreColumnType<?, ?>> typeRegistry;

        private Builder() {
            // Avoid direct initialization
        }

        /**
         * @param datastore the {@link Datastore} to use for the DB interactions
         * @return self for method chaining
         */
        public Builder setDatastore(Datastore datastore) {
            this.datastore = checkNotNull(datastore);
            return this;
        }

        /**
         * Switches the {@code DatastoreStorageFactory} that is being built to be or not to be multitenant.
         *
         * @param multitenant {@code true} if the {@code DatastoreStorageFactory} should be multitenant or not
         * @return self for method chaining
         */
        public Builder setMultitenant(boolean multitenant) {
            this.multitenant = multitenant;
            return this;
        }

        /**
         * Sets a {@link ColumnTypeRegistry} for handling the Entity Columns.
         *
         * <p>Default value is {@link DatastoreTypeRegistryFactory#defaultInstance()}.
         *
         * @param typeRegistry the type registry containing all the required
         * {@linkplain org.spine3.server.entity.storage.ColumnType column types} to handle the existing Entity Columns
         * @return self for method chaining
         */
        public Builder setTypeRegistry(ColumnTypeRegistry<? extends DatastoreColumnType<?, ?>> typeRegistry) {
            this.typeRegistry = checkNotNull(typeRegistry);
            return this;
        }

        /**
         * Creates a new instance of {@code DatastoreStorageFactory} with the passed parameters.
         *
         * <p>Precondition of a successful build is that the {@code datastore} field has been set.
         *
         * @return new instance of {@code DatastoreStorageFactory}
         */
        public DatastoreStorageFactory build() {
            checkNotNull(datastore);
            if (typeRegistry == null) {
                typeRegistry = DatastoreTypeRegistryFactory.defaultInstance();
            }
            return new DatastoreStorageFactory(this);
        }
    }
}
