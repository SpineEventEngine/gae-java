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
import io.spine.server.storage.StorageField;
import io.spine.string.Stringifiers;

import java.util.Date;

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
    static void addAggregateIdProperty(Entity.Builder entity, Object aggregateId) {
        final String propertyValue = Stringifiers.toString(aggregateId);
        entity.set(aggregate_id.toString(), propertyValue);
    }

    static void addCreatedProperty(Entity.Builder entity, Timestamp when) {
        final long millis = Timestamps.toMillis(when);
        final Date date = new Date(millis);
        final DateTime dateTime = DateTime.copyFrom(date);
        AggregateEventRecordProperty.created.setProperty(entity, dateTime);
    }

    static void addVersionProperty(Entity.Builder entity, Version version) {
        final int number = version.getNumber();
        AggregateEventRecordProperty.version.setProperty(entity, number);
    }

    static void markAsSnapshot(Entity.Builder entity, boolean snapshot) {
        AggregateEventRecordProperty.snapshot.setProperty(entity, snapshot);
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
        return AggregateEventRecordOrdering.BY_CREATED.getOrdering();
    }

    static OrderBy byVersion() {
        return AggregateEventRecordOrdering.BY_VERSION.getOrdering();
    }

    static OrderBy byRecordType() {
        return AggregateEventRecordOrdering.BY_SNAPSHOT.getOrdering();
    }

    private static boolean hasFlag(Entity entity, String flagName) {
        final boolean result = entity.contains(flagName)
                            && entity.getBoolean(flagName);
        return result;
    }

    private static OrderBy asc(StorageField property) {
        return OrderBy.asc(property.toString());
    }

    private static OrderBy desc(StorageField property) {
        return OrderBy.desc(property.toString());
    }

    private enum AggregateEventRecordProperty implements StorageField {

        /**
         * A property storing the Event creation time.
         */
        created {
            @Override
            void setProperty(Entity.Builder builder, Object value) {
                final DateTime dateTime = (DateTime) value;
                builder.set(toString(), dateTime);
            }
        },

        /**
         * A property storing the Aggregate version.
         */
        version {
            @Override
            void setProperty(Entity.Builder builder, Object value) {
                final int version = (int) value;
                builder.set(toString(), version);
            }
        },

        /**
         * A boolean property storing {@code true} if the Record represents a Snapshot and
         * {@code false} otherwise.
         */
        snapshot {
            @Override
            void setProperty(Entity.Builder builder, Object value) {
                final boolean isSnapshot = (boolean) value;
                builder.set(toString(), isSnapshot);
            }
        };

        abstract void setProperty(Entity.Builder builder, Object value);

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }
    }

    private enum AggregateEventRecordOrdering {

        BY_CREATED(desc(AggregateEventRecordProperty.created)),
        BY_VERSION(desc(AggregateEventRecordProperty.version)),
        BY_SNAPSHOT(asc(AggregateEventRecordProperty.snapshot));

        private final OrderBy ordering;

        AggregateEventRecordOrdering(OrderBy ordering) {
            this.ordering = ordering;
        }

        private OrderBy getOrdering() {
            return ordering;
        }
    }
}
