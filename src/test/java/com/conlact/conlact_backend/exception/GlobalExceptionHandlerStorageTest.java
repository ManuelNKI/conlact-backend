package com.conlact.conlact_backend.exception;

import com.conlact.conlact_backend.storage.StorageException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerStorageTest {
    @Test
    void returnsActionableAuthenticationErrorWithoutExposingProviderDetails() {
        var response = new GlobalExceptionHandler().handleStorageException(
                new StorageException("Invalid Compact JWS: internal-provider-details"),
                new MockHttpServletRequest("POST", "/api/admin/imagenes"));
        assertThat(response.getStatusCode().value()).isEqualTo(502);
        assertThat(response.getBody().message()).contains("autenticarse", "Supabase")
                .doesNotContain("internal-provider-details");
    }

    @Test
    void reportsOtherProviderFailuresWithoutLeakingRawResponse() {
        var response = new GlobalExceptionHandler().handleStorageException(
                new StorageException("upstream-private-response"),
                new MockHttpServletRequest("POST", "/api/admin/imagenes"));
        assertThat(response.getStatusCode().value()).isEqualTo(502);
        assertThat(response.getBody().message()).contains("Intenta nuevamente")
                .doesNotContain("upstream-private-response");
    }
}
