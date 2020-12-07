/*
 * Copyright 2020, TeamDev. All rights reserved.
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

import io.spine.core.TenantId;
import io.spine.net.EmailAddress;
import io.spine.net.InternetDomain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.protobuf.Messages.isDefault;

@DisplayName("`SingleTenantNamespaceSupplier` should")
class SingleTenantNamespaceSupplierTest {

    @Test
    @DisplayName("produce empty namespace")
    void testProduceEmpty() {
        NamespaceSupplier supplier = NamespaceSupplier.singleTenant();
        Namespace namespace = supplier.get();
        assertThat(namespace)
                .isNotNull();
        assertThat(namespace.value())
                .isEmpty();
        TenantId tenant = namespace.toTenantId();
        assertThat(isEffectivelyDefault(tenant))
                .isTrue();
    }

    @Test
    @DisplayName("produce custom namespace")
    void testProduceCustom() {
        String namespaceValue = "my-custom-namespace";
        NamespaceSupplier supplier = NamespaceSupplier.singleTenant(namespaceValue);
        Namespace namespace = supplier.get();
        assertThat(namespace)
                .isNotNull();
        assertThat(namespace.value())
                .isEqualTo(namespaceValue);

        TenantId tenant = namespace.toTenantId();
        assertThat(isEffectivelyDefault(tenant))
                .isFalse();
        assertThat(tenant.getValue())
                .isEqualTo(namespaceValue);
    }

    private static boolean isEffectivelyDefault(TenantId tenant) {
        InternetDomain domain = tenant.getDomain();
        if (!isDefault(domain)) {
            return false;
        }
        EmailAddress email = tenant.getEmail();
        if (!isDefault(email)) {
            return false;
        }
        String value = tenant.getValue();
        return value.isEmpty();
    }
}
