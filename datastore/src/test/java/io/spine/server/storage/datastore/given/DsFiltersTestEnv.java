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

package io.spine.server.storage.datastore.given;

import com.google.errorprone.annotations.Immutable;
import io.spine.server.entity.storage.Column;
import io.spine.server.entity.storage.ColumnName;

import static io.spine.server.storage.LifecycleFlagField.archived;
import static io.spine.server.storage.LifecycleFlagField.deleted;

public final class DsFiltersTestEnv {

    private DsFiltersTestEnv() {
    }

    @Immutable
    public static final class IdStringColumn implements Column {

        public static final ColumnName NAME = ColumnName.of("id_string");

        @Override
        public ColumnName name() {
            return NAME;
        }

        @Override
        public Class<?> type() {
            return String.class;
        }
    }

    @Immutable
    public static final class ArchivedColumn implements Column {

        public static final ColumnName NAME = ColumnName.of(archived);

        @Override
        public ColumnName name() {
            return NAME;
        }

        @Override
        public Class<?> type() {
            return boolean.class;
        }
    }

    @Immutable
    public static final class DeletedColumn implements Column {

        public static final ColumnName NAME = ColumnName.of(deleted);

        @Override
        public ColumnName name() {
            return NAME;
        }

        @Override
        public Class<?> type() {
            return boolean.class;
        }
    }
}