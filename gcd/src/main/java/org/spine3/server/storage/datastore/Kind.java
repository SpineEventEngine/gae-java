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

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Message;
import org.spine3.type.TypeName;
import org.spine3.type.TypeUrl;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A data transfer object representing a Datastore
 * <a href="https://cloud.google.com/datastore/docs/concepts/entities#kinds_and_identifiers>kind</a>.
 *
 * @author Dmytro Dashenkov
 */
public final class Kind {

    private static final String INVALID_KIND_ERROR_MESSAGE =
            "Datastore kind cannot start with \"__\". See https://cloud.google.com/datastore/docs/concepts/entities#kinds_and_identifiers for more info.";
    private static final String FORBIDDEN_PREFIX = "__";

    private final String value;

    private Kind(String value) {
        this.value = checkValidKind(value);
    }

    public static Kind of(String value) {
        return new Kind(value);
    }

    public static Kind of(TypeUrl typeUrl) {
        return new Kind(typeUrl.getTypeName());
    }

    public static Kind of(Descriptor descriptor) {
        return new Kind(descriptor.getFullName());
    }

    public static Kind of(Message message) {
        return of(message.getDescriptorForType());
    }

    public static Kind of(TypeName typeName) {
        return new Kind(typeName.value());
    }

    public String getValue() {
        return value;
    }

    private static String checkValidKind(String kind) {
        checkNotNull(kind);
        checkArgument(!kind.startsWith(FORBIDDEN_PREFIX), INVALID_KIND_ERROR_MESSAGE);
        return kind;
    }
}