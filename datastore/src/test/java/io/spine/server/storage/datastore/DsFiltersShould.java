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

import com.google.cloud.datastore.NullValue;
import com.google.cloud.datastore.StructuredQuery.PropertyFilter;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import io.spine.client.ColumnFilter;
import io.spine.server.entity.AbstractVersionableEntity;
import io.spine.server.entity.Entity;
import io.spine.server.entity.TestEntityWithStringColumn;
import io.spine.server.entity.storage.CompositeQueryParameter;
import io.spine.server.entity.storage.EntityColumn;
import io.spine.test.storage.Project;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;

import static com.google.cloud.datastore.StructuredQuery.CompositeFilter.and;
import static com.google.cloud.datastore.StructuredQuery.Filter;
import static com.google.common.collect.ImmutableMultimap.of;
import static io.spine.client.ColumnFilters.eq;
import static io.spine.client.ColumnFilters.ge;
import static io.spine.client.ColumnFilters.gt;
import static io.spine.client.ColumnFilters.le;
import static io.spine.client.ColumnFilters.lt;
import static io.spine.client.CompositeColumnFilter.CompositeOperator.ALL;
import static io.spine.client.CompositeColumnFilter.CompositeOperator.EITHER;
import static io.spine.server.entity.storage.TestCompositeQueryParameterFactory.createParams;
import static io.spine.server.storage.LifecycleFlagField.archived;
import static io.spine.server.storage.LifecycleFlagField.deleted;
import static io.spine.server.storage.datastore.DsFilters.fromParams;
import static io.spine.server.storage.datastore.type.DatastoreTypeRegistryFactory.defaultInstance;
import static io.spine.test.Tests.assertHasPrivateParameterlessCtor;
import static io.spine.test.Verify.assertContainsAll;
import static io.spine.test.Verify.assertSize;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Dmytro Dashenkov
 */
public class DsFiltersShould {

    private static final String ID_STRING_GETTER_NAME = "getIdString";
    private static final String ID_STRING_COLUMN_NAME = "idString";
    private static final String DELETED_GETTER_NAME = "isDeleted";
    private static final String ARCHIVED_GETTER_NAME = "isArchived";

    @Test
    public void have_private_util_ctor() {
        assertHasPrivateParameterlessCtor(DsFilters.class);
    }

    @Test
    public void generate_filters_from_composite_query_params() {
        String idStringValue = "42";
        boolean archivedValue = true;
        boolean deletedValue = true;
        Multimap<EntityColumn, ColumnFilter> conjunctiveFilters = of(
                column(TestEntity.class, ID_STRING_GETTER_NAME), gt(ID_STRING_COLUMN_NAME,
                                                                           idStringValue)
        );
        ImmutableMultimap<EntityColumn, ColumnFilter> disjunctiveFilters = of(
                column(TestEntity.class, DELETED_GETTER_NAME), eq(deleted.name(), deletedValue),
                column(TestEntity.class, ARCHIVED_GETTER_NAME), eq(archived.name(), archivedValue)
        );
        Collection<CompositeQueryParameter> parameters = ImmutableSet.of(
                createParams(conjunctiveFilters, ALL),
                createParams(disjunctiveFilters, EITHER)
        );

        ColumnFilterAdapter columnFilterAdapter = ColumnFilterAdapter.of(defaultInstance());
        Collection<Filter> filters = fromParams(parameters, columnFilterAdapter);
        assertContainsAll(filters, and(PropertyFilter.gt(ID_STRING_COLUMN_NAME, idStringValue),
                                       PropertyFilter.eq(archived.name(), archivedValue)),
                                   and(PropertyFilter.gt(ID_STRING_COLUMN_NAME, idStringValue),
                                       PropertyFilter.eq(deleted.name(), deletedValue)));
    }

    @Test
    public void generate_filters_from_single_parameter() {
        String versionValue = "314";
        ImmutableMultimap<EntityColumn, ColumnFilter> singleFilter = of(
                column(TestEntity.class, ID_STRING_GETTER_NAME), le(ID_STRING_COLUMN_NAME,
                                                                    versionValue)
        );
        Collection<CompositeQueryParameter> parameters = ImmutableSet.of(
                createParams(singleFilter, ALL)
        );

        ColumnFilterAdapter columnFilterAdapter = ColumnFilterAdapter.of(defaultInstance());
        Collection<Filter> filters = fromParams(parameters, columnFilterAdapter);
        assertContainsAll(filters, and(PropertyFilter.le(ID_STRING_COLUMN_NAME, versionValue)));
    }

    @Test
    public void generate_filters_for_multiple_disjunctive_groups() {
        String greaterBoundDefiner = "271";
        String standaloneValue = "100";
        String lessBoundDefiner = "42";
        boolean archivedValue = true;
        boolean deletedValue = true;
        EntityColumn idStringColumn = column(TestEntity.class, ID_STRING_GETTER_NAME);
        ImmutableMultimap<EntityColumn, ColumnFilter> versionFilters = of(
                idStringColumn, ge(ID_STRING_COLUMN_NAME, greaterBoundDefiner),
                idStringColumn, eq(ID_STRING_COLUMN_NAME, standaloneValue),
                idStringColumn, lt(ID_STRING_COLUMN_NAME, lessBoundDefiner)
        );
        ImmutableMultimap<EntityColumn, ColumnFilter> lifecycleFilters = of(
                column(TestEntity.class, DELETED_GETTER_NAME), eq(deleted.name(),
                                                                  deletedValue),
                column(TestEntity.class, ARCHIVED_GETTER_NAME), eq(archived.name(),
                                                                   archivedValue)
        );
        Collection<CompositeQueryParameter> parameters = ImmutableSet.of(
                createParams(versionFilters, EITHER),
                createParams(lifecycleFilters, EITHER)
        );
        ColumnFilterAdapter columnFilterAdapter = ColumnFilterAdapter.of(defaultInstance());
        Collection<Filter> filters = fromParams(parameters, columnFilterAdapter);
        assertContainsAll(filters,
                          and(PropertyFilter.ge(ID_STRING_COLUMN_NAME, greaterBoundDefiner),
                              PropertyFilter.eq(archived.name(), archivedValue)),
                          and(PropertyFilter.ge(ID_STRING_COLUMN_NAME, greaterBoundDefiner),
                              PropertyFilter.eq(deleted.name(), deletedValue)),
                          and(PropertyFilter.eq(ID_STRING_COLUMN_NAME, standaloneValue),
                              PropertyFilter.eq(archived.name(), archivedValue)),
                          and(PropertyFilter.eq(ID_STRING_COLUMN_NAME, standaloneValue),
                              PropertyFilter.eq(deleted.name(), deletedValue)),
                          and(PropertyFilter.lt(ID_STRING_COLUMN_NAME, lessBoundDefiner),
                              PropertyFilter.eq(archived.name(), archivedValue)),
                          and(PropertyFilter.lt(ID_STRING_COLUMN_NAME, lessBoundDefiner),
                              PropertyFilter.eq(deleted.name(), deletedValue)));
    }

    @Test
    public void generate_filters_from_empty_params() {
        Collection<CompositeQueryParameter> parameters = Collections.emptySet();
        Collection<Filter> filters = fromParams(parameters,
                                                      ColumnFilterAdapter.of(defaultInstance()));
        assertNotNull(filters);
        assertSize(0, filters);
    }

    //TODO:2018-06-08:dmytro.kuzmin: re-write without mocks when null column filters are available.
    // See https://github.com/SpineEventEngine/core-java/issues/720.
    @Test
    public void generate_filters_for_null_column_value() {
        EntityColumn column = mock(EntityColumn.class);
        when(column.getStoredName()).thenReturn(ID_STRING_COLUMN_NAME);
        when(column.getType()).thenReturn(String.class);
        when(column.getPersistedType()).thenReturn(String.class);
        when(column.toPersistedValue(any())).thenReturn(null);

        ColumnFilter filterWithStubValue = eq(ID_STRING_COLUMN_NAME, "");
        ImmutableMultimap<EntityColumn, ColumnFilter> filter = of(
                column, filterWithStubValue
        );
        Collection<CompositeQueryParameter> parameters = ImmutableSet.of(
                createParams(filter, ALL)
        );

        ColumnFilterAdapter columnFilterAdapter = ColumnFilterAdapter.of(defaultInstance());
        Collection<Filter> filters = fromParams(parameters, columnFilterAdapter);
        assertContainsAll(filters, and(PropertyFilter.eq(ID_STRING_COLUMN_NAME, NullValue.of())));
    }

    private static EntityColumn column(Class<? extends Entity> cls, String methodName) {
        Method method = null;
        try {
            method = cls.getMethod(methodName);
        } catch (NoSuchMethodException e) {
            fail("Method " + methodName + " not found.");
        }
        EntityColumn column = EntityColumn.from(method);
        return column;
    }

    private static class TestEntity
            extends AbstractVersionableEntity<String, Project>
            implements TestEntityWithStringColumn {

        protected TestEntity(String id) {
            super(id);
        }

        @Override
        public String getIdString() {
            return getId();
        }
    }
}
