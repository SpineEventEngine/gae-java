/*
 * Copyright 2021, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

package io.spine.server.storage.datastore.query;

import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.Value;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.Message;
import io.spine.query.ComparisonOperator;
import io.spine.query.LogicalOperator;
import io.spine.query.QueryPredicate;
import io.spine.query.Subject;
import io.spine.query.SubjectParameter;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.function.Predicate;

import static io.spine.util.Exceptions.newIllegalArgumentException;

/**
 * Test if a Datastore entity matches query parameters.
 */
final class ColumnPredicate<I, R extends Message> implements Predicate<Entity> {

    private final Subject<I, R> querySubject;

    private final FilterAdapter adapter;

    ColumnPredicate(Subject<I, R> querySubject, FilterAdapter adapter) {
        this.querySubject = querySubject;
        this.adapter = adapter;
    }

    @Override
    @SuppressWarnings("FallThrough") // defines strategy for default and faulty values.
    public boolean test(@Nullable Entity entity) {
        if (entity == null) {
            return false;
        }

        QueryPredicate<R> root = querySubject.predicate();
        boolean result = testPredicate(root, entity);
        return result;
    }

    private boolean testPredicate(QueryPredicate<R> predicate, Entity entity) {
        boolean match;
        LogicalOperator operator = predicate.operator();
        ImmutableList<SubjectParameter<?, ?, ?>> parameters = predicate.allParams();
        ImmutableList<QueryPredicate<R>> children = predicate.children();
        switch (operator) {
            case AND:
                match = checkAnd(entity, parameters, children);
                break;
            case OR:
                match = checkOr(entity, parameters, children);
                break;
            default:
                throw newIllegalArgumentException(
                        "Unknown logical operator `%s`.", operator
                );
        }
        return !match;
    }

    @SuppressWarnings("MethodWithMultipleLoops")    /* To avoid multiple similar methods. */
    private boolean checkAnd(Entity entity,
                             ImmutableList<SubjectParameter<?, ?, ?>> params,
                             ImmutableList<QueryPredicate<R>> children) {
        for (SubjectParameter<?, ?, ?> param : params) {
            boolean matches = checkParamValue(param, entity);
            if (!matches) {
                return false;
            }
        }
        for (QueryPredicate<R> child : children) {
            boolean matches = testPredicate(child, entity);
            if (!matches) {
                return false;
            }
        }

        return true;
    }

    @SuppressWarnings("MethodWithMultipleLoops")    /* To avoid multiple similar methods. */
    private boolean checkOr(Entity entity,
                            ImmutableList<SubjectParameter<?, ?, ?>> params,
                            ImmutableList<QueryPredicate<R>> children) {
        for (SubjectParameter<?, ?, ?> param : params) {
            boolean matches = checkParamValue(param, entity);
            if (matches) {
                return true;
            }
        }
        for (QueryPredicate<R> child : children) {
            boolean matches = testPredicate(child, entity);
            if (matches) {
                return true;
            }
        }
        return params.isEmpty();
    }

    private boolean checkParamValue(SubjectParameter<?, ?, ?> parameter, Entity entity) {
        String columnName = parameter.column()
                                     .name()
                                     .value();
        if (!entity.contains(columnName)) {
            return false;
        }
        Value<?> typedActual = entity.getValue(columnName);
        Object actual = typedActual.get();
        Value<?> typedExpected = adapter.transformValue(parameter);
        Object expected = typedExpected.get();
        ComparisonOperator operator = parameter.operator();
        boolean result = operator.eval(actual, expected);
        return result;
    }
}
