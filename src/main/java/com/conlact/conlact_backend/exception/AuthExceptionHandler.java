package com.conlact.conlact_backend.exception;

import com.conlact.conlact_backend.controller.AuthController;
import com.conlact.conlact_backend.email.EmailDeliveryException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionHandler {

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<Map<String, Object>> rateLimit(RateLimitException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfterSeconds()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body("RATE_LIMITED", ex.getMessage()));
    }

    @ExceptionHandler(EmailDeliveryException.class)
    public ResponseEntity<Map<String, Object>> smtp(EmailDeliveryException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body("SMTP_UNAVAILABLE", "No se pudo enviar el código. Inténtalo de nuevo."));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, Object>> conflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body("CONFLICT", ex.getMessage()));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, Object>> badRequest(BadRequestException ex) {
        return ResponseEntity.badRequest()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body("VALIDATION_ERROR", ex.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> status(ResponseStatusException ex) {
        int code = ex.getStatusCode().value();
        String error = ex.getReason() == null ? "No se pudo completar la operación." : ex.getReason();
        String stable = code == 401 ? "UNAUTHENTICATED" : code == 403 ? "FORBIDDEN" : "REQUEST_FAILED";
        return ResponseEntity.status(code)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body(stable, error));
    }

    private Map<String, Object> body(String code, String error) {
        return Map.of("success", false, "code", code, "error", error);
    }
}
