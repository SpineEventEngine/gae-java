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

import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import io.spine.annotation.Internal;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.ContextSpec;
import io.spine.server.aggregate.Aggregate;
import io.spine.server.aggregate.AggregateStorage;
import io.spine.server.delivery.InboxStorage;
import io.spine.server.entity.Entity;
import io.spine.server.entity.storage.ColumnTypeRegistry;
import io.spine.server.projection.Projection;
import io.spine.server.projection.ProjectionStorage;
import io.spine.server.storage.RecordStorage;
import io.spine.server.storage.StorageFactory;
import io.spine.server.storage.datastore.tenant.DatastoreTenants;
import io.spine.server.storage.datastore.tenant.NamespaceConverter;
import io.spine.server.storage.datastore.tenant.NamespaceSupplier;
import io.spine.server.storage.datastore.tenant.TenantConverterRegistry;
import io.spine.server.storage.datastore.type.DatastoreColumnType;
import io.spine.server.storage.datastore.type.DatastoreTypeRegistryFactory;
import io.spine.server.tenant.TenantIndex;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static io.spine.server.entity.model.EntityClass.asEntityClass;
import static io.spine.server.storage.datastore.DatastoreWrapper.wrap;

/**
 * Creates storages based on {@link Datastore}.
 *
 * <p>As a convenience API, provides an ability to configure the {@link BoundedContextBuilder}s
 * with the {@link TenantIndex} specific to the instance of {@code Datastore} configured for this
 * factory
 *
 * @see DatastoreStorageFactory#configureTenantIndex(BoundedContextBuilder)
 */
public class DatastoreStorageFactory implements StorageFactory {

    private static final String DEFAULT_NAMESPACE_ERROR_MESSAGE =
            "Datastore namespace should not be configured explicitly" +
                    "for a multitenant storage";

    private final Datastore datastore;

    /**
     * Cached instances of datastore wrappers per {@code ContextSpec}.
     *
     * <p>The repeated calls of the methods of this factory should refer to the same instance of
     * the wrapped {@code Datastore}. Then the storage configuration for the repositories
     * of the same {@code BoundedContext} is consistent.
     *
     */
    private final Map<ContextSpec, DatastoreWrapper> wrappers = Maps.newConcurrentMap();
    private final ColumnTypeRegistry<? extends DatastoreColumnType<?, ?>> typeRegistry;

    DatastoreStorageFactory(Builder builder) {
        this.typeRegistry = builder.typeRegistry;
        this.datastore = builder.datastore;
    }

    /**
     * Configures the passed {@link BoundedContextBuilder} with the {@link TenantIndex} built on
     * top of the {@code Datastore} specific to this factory instance.
     *
     * @param builder
     *         the instance of the builder to configure the tenant index for
     * @return the same instance of the builder, but with the tenant index set
     */
    public BoundedContextBuilder configureTenantIndex(BoundedContextBuilder builder) {
        TenantIndex index = DatastoreTenants.index(datastore);
        builder.setTenantIndex(index);
        return builder;
    }

    @Override
    public <I> AggregateStorage<I>
    createAggregateStorage(ContextSpec context, Class<? extends Aggregate<I, ?, ?>> cls) {
        checkNotNull(cls);
        DsAggregateStorage<I> result =
                new DsAggregateStorage<>(cls, datastoreFor(context), context.isMultitenant());
        return result;
    }

    @Override
    public <I> RecordStorage<I>
    createRecordStorage(ContextSpec context, Class<? extends Entity<I, ?>> cls) {
        DsRecordStorage<I> result = configure(DsRecordStorage.newBuilder(), cls, context).build();
        return result;
    }

    @Override
    public <I> ProjectionStorage<I>
    createProjectionStorage(ContextSpec context, Class<? extends Projection<I, ?, ?>> cls) {
        DsProjectionStorageDelegate<I> recordStorage =
                configure(DsProjectionStorageDelegate.newDelegateBuilder(), cls, context).build();
        DsPropertyStorage propertyStorage = createPropertyStorage(context);
        DsProjectionStorage<I> result =
                new DsProjectionStorage<>(cls,
                                          recordStorage,
                                          propertyStorage,
                                          context.isMultitenant());
        return result;
    }

    @Override
    public InboxStorage createInboxStorage(boolean multitenant) {
        return null;
    }

    public ColumnTypeRegistry getTypeRegistry() {
        return typeRegistry;
    }

    /**
     * Configures the passed builder of the storage to serve the passed entity class.
     */
    private <I, B extends RecordStorageBuilder<I, B>>
    B configure(B builder, Class<? extends Entity<I, ?>> cls, ContextSpec context) {

        builder.setModelClass(asEntityClass(cls))
               .setDatastore(datastoreFor(context))
               .setMultitenant(context.isMultitenant())
               .setColumnTypeRegistry(typeRegistry);
        return builder;
    }

    protected DsPropertyStorage createPropertyStorage(ContextSpec spec) {
        DatastoreWrapper datastore = datastoreFor(spec);
        DsPropertyStorage propertyStorage = DsPropertyStorage.newInstance(datastore);
        return propertyStorage;
    }

    private NamespaceSupplier createNamespaceSupplier(ContextSpec contextSpec) {
        @Nullable String defaultNamespace;
        boolean multitenant = contextSpec.isMultitenant();
        if (multitenant) {
            checkHasNoNamespace(datastore);
            defaultNamespace = null;
        } else {
            defaultNamespace = datastore.getOptions()
                                        .getNamespace();
        }
        ProjectId projectId = ProjectId.of(datastore);
        NamespaceSupplier result =
                NamespaceSupplier.instance(multitenant, defaultNamespace, projectId);
        return result;
    }

    private static void checkHasNoNamespace(Datastore datastore) {
        checkNotNull(datastore);
        DatastoreOptions options = datastore.getOptions();
        String namespace = options.getNamespace();
        checkArgument(isNullOrEmpty(namespace), DEFAULT_NAMESPACE_ERROR_MESSAGE);
    }

    /**
     * Performs no action.
     */
    @Override
    public void close() {
        // NOP
    }

    /**
     * Returns the currently known initialized {@code DatastoreWrapper}s.
     */
    @VisibleForTesting
    protected Map<ContextSpec, DatastoreWrapper> wrappers() {
        return ImmutableMap.copyOf(wrappers);
    }

    /**
     * Returs the instance of wrapped {@link Datastore}.
     */
    @VisibleForTesting
    protected Datastore datastore() {
        return datastore;
    }

    /**
     * Returns the instance of {@link DatastoreWrapper} based on the passed {@code ContextSpec}.
     *
     * <p>If there were no {@code DatastoreWrapper} instances created for the given context,
     * creates it.
     */
    @VisibleForTesting
    final DatastoreWrapper datastoreFor(ContextSpec spec) {
        if (!wrappers.containsKey(spec)) {
            DatastoreWrapper wrapper = createDatastoreWrapper(spec);
            wrappers.put(spec, wrapper);
        }
        return wrappers.get(spec);
    }

    /**
     * Creates an instance of {@link DatastoreWrapper} based on the passed {@code ContextSpec}.
     */
    @Internal
    @VisibleForTesting
    protected DatastoreWrapper createDatastoreWrapper(ContextSpec spec) {
        NamespaceSupplier supplier = createNamespaceSupplier(spec);
        return wrap(datastore, supplier);
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
        private ColumnTypeRegistry<? extends DatastoreColumnType<?, ?>> typeRegistry;
        private NamespaceConverter namespaceConverter;

        /** Avoid direct initialization. */
        private Builder() {
        }

        /**
         * Assigns the {@link Datastore} to use for the DB interactions.
         */
        public Builder setDatastore(Datastore datastore) {
            this.datastore = checkNotNull(datastore);
            return this;
        }

        public Datastore getDatastore() {
            return this.datastore;
        }

        /**
         * Sets a {@link ColumnTypeRegistry} for handling the Entity Columns.
         *
         * <p>Default value is {@link DatastoreTypeRegistryFactory#defaultInstance()}.
         *
         * @param typeRegistry
         *         the type registry containing all the required
         *         {@linkplain io.spine.server.entity.storage.ColumnType column types}
         *         to handle the existing Entity Columns
         * @return self for method chaining
         */
        public Builder
        setTypeRegistry(ColumnTypeRegistry<? extends DatastoreColumnType<?, ?>> typeRegistry) {
            this.typeRegistry = checkNotNull(typeRegistry);
            return this;
        }

        /**
         * Sets a {@link NamespaceConverter} for converting the Datastore namespaces and
         * the {@link io.spine.core.TenantId Tenant IDs} back and forth.
         *
         * <p>Setting this parameter is reasonable (but not required) only if the storage is
         * multitenant. Otherwise, an exception will be thrown on {@linkplain #build() build}.
         *
         * @param converter
         *         a custom converter for the Tenant IDs
         * @return self for method chaining
         */
        public Builder setNamespaceConverter(NamespaceConverter converter) {
            this.namespaceConverter = checkNotNull(converter);
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
            if (namespaceConverter != null) {
                ProjectId projectId = ProjectId.of(datastore);
                TenantConverterRegistry.registerNamespaceConverter(projectId, namespaceConverter);
            }

            return new DatastoreStorageFactory(this);
        }
    }
}
