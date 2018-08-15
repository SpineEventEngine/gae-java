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

package io.spine.server.storage.datastore.tenant;

import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.Query;
import com.google.cloud.datastore.QueryResults;
import com.google.common.testing.NullPointerTester;
import io.spine.core.TenantId;
import io.spine.net.InternetDomain;
import io.spine.server.storage.datastore.ProjectId;
import io.spine.server.storage.datastore.given.Given;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Throwables.getStackTraceAsString;
import static io.spine.server.storage.datastore.given.TestCases.DO_NOTHING_ON_CLOSE;
import static io.spine.server.storage.datastore.given.TestCases.HAVE_PRIVATE_UTILITY_CTOR;
import static io.spine.server.storage.datastore.tenant.Namespace.of;
import static io.spine.testing.Verify.assertContains;
import static io.spine.testing.Verify.assertContainsAll;
import static io.spine.testing.Verify.assertSize;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Dmytro Dashenkov
 */
@DisplayName("NamespaceIndex should")
class NamespaceIndexTest {

    private static final String TENANT_ID_STRING = "some-tenant";

    @Test
    @DisplayName(HAVE_PRIVATE_UTILITY_CTOR)
    void testNulls() {
        Namespace defaultNamespace = of("some-string");
        TenantId tenantId = TenantId.getDefaultInstance();

        new NullPointerTester()
                .setDefault(Namespace.class, defaultNamespace)
                .setDefault(TenantId.class, tenantId)
                .testInstanceMethods(new NamespaceIndex(mockDatastore(), true),
                                     NullPointerTester.Visibility.PACKAGE);
    }

    @Test
    @DisplayName("store tenant IDs")
    void testStore() {
        NamespaceIndex namespaceIndex = new NamespaceIndex(mockDatastore(), true);

        Set<TenantId> initialEmptySet = namespaceIndex.getAll();
        assertTrue(initialEmptySet.isEmpty());

        TenantId newId = TenantId.newBuilder()
                                 .setValue(TENANT_ID_STRING)
                                 .build();
        namespaceIndex.keep(newId);

        Set<TenantId> ids = namespaceIndex.getAll();
        assertFalse(ids.isEmpty());
        assertSize(1, ids);

        TenantId actual = ids.iterator()
                             .next();
        assertEquals(newId, actual);
    }

    @Test
    @DisplayName(DO_NOTHING_ON_CLOSE)
    void testClose() {
        NamespaceIndex namespaceIndex = new NamespaceIndex(mockDatastore(), false);

        namespaceIndex.close();
        namespaceIndex.close();
        // No exception is thrown on the second call to #close() => no operation is performed
    }

    @Test
    @DisplayName("find existing namespaces")
    void testFindExisting() {
        NamespaceIndex namespaceIndex = new NamespaceIndex(mockDatastore(), true);

        // Ensure no namespace has been kept
        Set<TenantId> initialEmptySet = namespaceIndex.getAll();
        assertTrue(initialEmptySet.isEmpty());

        TenantId newId = TenantId.newBuilder()
                                 .setValue(TENANT_ID_STRING)
                                 .build();
        Namespace newNamespace = of(newId, Given.testProjectId());

        namespaceIndex.keep(newId);
        assertTrue(namespaceIndex.contains(newNamespace));
    }

    @Test
    @DisplayName("not find non existing namespaces")
    void testNotFindNonExisting() {
        NamespaceIndex namespaceIndex = new NamespaceIndex(mockDatastore(), true);

        // Ensure no namespace has been kept
        Set<TenantId> initialEmptySet = namespaceIndex.getAll();
        assertTrue(initialEmptySet.isEmpty());

        TenantId fakeId = TenantId.newBuilder()
                                  .setValue(TENANT_ID_STRING)
                                  .build();
        Namespace fakeNamespace = of(fakeId, ProjectId.of("fake-prj"));

        assertFalse(namespaceIndex.contains(fakeNamespace));
    }

    @Test
    @DisplayName("synchronize access methods")
    void testAsync() {
        assertTimeout(Duration.ofSeconds(5L),
                      NamespaceIndexTest::testSynchronizeAccessMethods);
    }

    @SuppressWarnings("OverlyLongMethod")
    private static void testSynchronizeAccessMethods() throws InterruptedException {
        // Initial data
        Collection<Key> keys = new LinkedList<>();
        keys.add(mockKey("Vtenant1"));
        keys.add(mockKey("Vtenant2"));
        keys.add(mockKey("Vtenant3"));
        Collection<TenantId> initialTenantIds =
                keys.stream()
                    .map(key -> TenantId.newBuilder()
                                        .setValue(key.getName().substring(1))
                                        .build())
                    .collect(toList());

        NamespaceIndex.NamespaceQuery namespaceQuery = keys::iterator;
        // The tested object
        NamespaceIndex namespaceIndex = new NamespaceIndex(namespaceQuery,
                                                           Given.testProjectId(),
                                                           true);

        // The test flow
        Runnable flow = () -> {
            // Initial value check
            Set<TenantId> initialIdsActual = namespaceIndex.getAll(); // sync
            // The keep may already be called
            assertThat(initialIdsActual.size(), greaterThanOrEqualTo(initialTenantIds.size()));
            @SuppressWarnings("ZeroLengthArrayAllocation") TenantId[] elements = initialTenantIds.toArray(
                    new TenantId[0]);
            assertContainsAll(initialIdsActual, elements);

            // Add new element
            InternetDomain domain = InternetDomain.newBuilder()
                                                  .setValue("my.tenant.com")
                                                  .build();
            TenantId newTenantId = TenantId.newBuilder()
                                           .setDomain(domain)
                                           .build();
            namespaceIndex.keep(newTenantId); // sync

            // Check new value added
            boolean success = namespaceIndex.contains(of(newTenantId,    // sync
                                                         Given.testProjectId()));
            assertTrue(success);

            // Check returned set has newly added element
            Set<TenantId> updatedIds = namespaceIndex.getAll(); // sync
            assertEquals(updatedIds.size(), initialTenantIds.size() + 1);
            assertContains(newTenantId, updatedIds);
        };

        // Test execution threads
        Thread firstThread = new Thread(flow);
        Thread secondThread = new Thread(flow);

        // Collect thread failures
        Map<Thread, Throwable> threadFailures = new HashMap<>(2);
        Thread.UncaughtExceptionHandler throwableCollector = threadFailures::put;

        firstThread.setUncaughtExceptionHandler(throwableCollector);
        secondThread.setUncaughtExceptionHandler(throwableCollector);

        // Start parallel execution
        firstThread.start();
        secondThread.start();

        // Await both threads to complete
        firstThread.join();
        secondThread.join();

        // Check for failures
        // Throw if any, failing the test
        for (Throwable failure : threadFailures.values()) {
            fail(format("Test thread has thrown a Throwable. %s",
                        getStackTraceAsString(failure)));
        }
    }

    private static Key mockKey(String name) {
        Key key = Key.newBuilder("some-proj", "some-kind", name)
                     .build();
        return key;
    }

    private static Datastore mockDatastore() {
        Datastore datastore = mock(Datastore.class);
        DatastoreOptions options = mock(DatastoreOptions.class);
        when(datastore.getOptions()).thenReturn(options);
        when(options.getProjectId()).thenReturn("some-project-id-NamespaceIndexTest");
        QueryResults results = mock(QueryResults.class);
        when(datastore.run(any(Query.class))).thenReturn(results);
        return datastore;
    }
}