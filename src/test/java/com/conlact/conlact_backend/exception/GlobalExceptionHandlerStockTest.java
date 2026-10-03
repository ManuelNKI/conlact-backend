package com.conlact.conlact_backend.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerStockTest {

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Test
    @DisplayName("Debe manejar InsufficientStockException retornando HTTP 409 Conflict")
    void testHandleInsufficientStockException() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/admin/variants/1/stock/deduct");

        InsufficientStockException ex = new InsufficientStockException("Stock insuficiente para la variante 'Cuña 250g'");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleInsufficientStockException(ex, request);

        assertNotNull(response);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().status());
        assertEquals("Conflict", response.getBody().error());
        assertEquals("Stock insuficiente para la variante 'Cuña 250g'", response.getBody().message());
    }

    @Test
    @DisplayName("Debe manejar ObjectOptimisticLockingFailureException retornando HTTP 409 Conflict")
    void testHandleOptimisticLockingFailureException() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/admin/variants/1/stock/deduct");

        ObjectOptimisticLockingFailureException ex = new ObjectOptimisticLockingFailureException(
                "ProductVariant", "1");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleOptimisticLockingFailureException(ex, request);

        assertNotNull(response);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().status());
        assertTrue(response.getBody().message().contains("Conflicto de concurrencia"));
    }
}
