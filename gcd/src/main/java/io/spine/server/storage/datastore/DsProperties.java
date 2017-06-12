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

import com.google.cloud.datastore.DateTime;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.StructuredQuery.OrderBy;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import io.spine.base.Version;
import io.spine.server.storage.LifecycleFlagField;
import io.spine.string.Stringifiers;

import java.util.Date;

import static com.google.cloud.datastore.StructuredQuery.OrderBy.asc;
import static com.google.cloud.datastore.StructuredQuery.OrderBy.desc;
import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.server.aggregate.storage.AggregateField.aggregate_id;
import static io.spine.server.storage.LifecycleFlagField.archived;

/**
 * Utility class, which simplifies creation of the Datastore properties.
 *
 * @author Mikhail Mikhaylov
 * @author Dmytro Dashenkov
 */
@SuppressWarnings("UtilityClass")
final class DsProperties {

    private DsProperties() {
        // Prevent utility class instantiation.
    }

    /**
     * Makes AggregateId property from given {@link Message} value.
     */
    static void addAggregateIdProperty(Object aggregateId, Entity.Builder entity) {
        final String propertyValue = Stringifiers.toString(aggregateId);
        entity.set(aggregate_id.toString(), propertyValue);
    }

    static void addCreatedProperty(Timestamp when, Entity.Builder entity) {
        final long millis = Timestamps.toMillis(when);
        final Date date = new Date(millis);
        final DateTime dateTime = DateTime.copyFrom(date);
        entity.set(AggregateEventRecordProperty.CREATED.name(), dateTime);
    }

    static void addVersionProperty(Version version, Entity.Builder entity) {
        final int number = version.getNumber();
        entity.set(AggregateEventRecordProperty.VERSION.name(), number);
    }

    static void markSnapshotProperty(boolean snapshot, Entity.Builder entity) {
        entity.set(AggregateEventRecordProperty.SNAPSHOT.name(), snapshot);
    }

    static void addArchivedProperty(Entity.Builder entity, boolean archived) {
        entity.set(LifecycleFlagField.archived.toString(), archived);
    }

    static void addDeletedProperty(Entity.Builder entity, boolean deleted) {
        entity.set(LifecycleFlagField.deleted.toString(), deleted);
    }

    static boolean isArchived(Entity entity) {
        checkNotNull(entity);
        return hasFlag(entity, archived.toString());
    }

    static boolean isDeleted(Entity entity) {
        checkNotNull(entity);
        return hasFlag(entity, LifecycleFlagField.deleted.toString());
    }

    static OrderBy byCreatedTime() {
        return AggregateEventRecordOrdering.BY_CREATED_PROPERTY.getOrdering();
    }

    static OrderBy byVersion() {
        return AggregateEventRecordOrdering.BY_VERSION_PROPERTY.getOrdering();
    }

    static OrderBy byRecordType() {
        return AggregateEventRecordOrdering.BY_SNAPSHOT_PROPERTY.getOrdering();
    }

    private static boolean hasFlag(Entity entity, String flagName) {
        final boolean result =
                entity.contains(flagName)
                        && entity.getBoolean(flagName);
        return result;
    }

    private enum AggregateEventRecordProperty {

        CREATED,
        VERSION,
        SNAPSHOT;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    private enum AggregateEventRecordOrdering {

        BY_CREATED_PROPERTY(desc(AggregateEventRecordProperty.CREATED.toString())),
        BY_VERSION_PROPERTY(desc(AggregateEventRecordProperty.VERSION.toString())),
        BY_SNAPSHOT_PROPERTY(asc(AggregateEventRecordProperty.SNAPSHOT.toString()));

        private final OrderBy ordering;

        AggregateEventRecordOrdering(OrderBy ordering) {
            this.ordering = ordering;
        }

        private OrderBy getOrdering() {
            return ordering;
        }
    }
}
