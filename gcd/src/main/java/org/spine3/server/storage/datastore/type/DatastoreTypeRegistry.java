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

package org.spine3.server.storage.datastore.type;

import com.google.protobuf.Timestamp;
import org.spine3.server.entity.storage.ColumnTypeRegistry;

import static org.spine3.server.storage.datastore.type.DsColumnTypes.*;

/**
 * @author Dmytro Dashenkov
 */
public class DatastoreTypeRegistry {

    private static final ColumnTypeRegistry<DatastoreColumnType> DEFAULT_REGISTRY =
            ColumnTypeRegistry.<DatastoreColumnType>newBuilder()
                              .put(String.class, stringType())
                              .put(Integer.class, integerType())
                              .put(Boolean.class, booleanType())
                              .put(Timestamp.class, timestampType())
                              .build();

    private DatastoreTypeRegistry() {
        // Prevent initialization of a utility class
    }

    public static ColumnTypeRegistry<DatastoreColumnType> defaultInstance() {
        return DEFAULT_REGISTRY;
    }

    public static ColumnTypeRegistry.Builder<DatastoreColumnType> predifinedValuesAnd() {
        return ColumnTypeRegistry.newBuilder(DEFAULT_REGISTRY);
    }
}