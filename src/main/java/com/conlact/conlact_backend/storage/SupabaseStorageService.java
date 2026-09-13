package com.conlact.conlact_backend.storage;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/**
 * Implementación de {@link IStorage} sobre la API REST de Supabase Storage.
 * Utiliza el token con rol de servicio (service_role) para gestionar de forma
 * centralizada los buckets de imágenes y comprobantes de pago.
 */
@Slf4j
@Service
public class SupabaseStorageService implements IStorage {

    private final String supabaseUrl;
    private final String storageBaseUrl;
    private final String serviceRoleKey;
    private final String productBucket;
    private final String recipeBucket;
    private final String paymentBucket;
    private final RestClient restClient;

    @Autowired
    public SupabaseStorageService(
            @Value("${app.supabase.url}") String supabaseUrl,
            @Value("${app.supabase.service-role-key}") String serviceRoleKey,
            @Value("${app.supabase.storage.product-bucket:product-images}") String productBucket,
            @Value("${app.supabase.storage.recipe-bucket:recipe-images}") String recipeBucket,
            @Value("${app.supabase.storage.payment-bucket:payment-proofs}") String paymentBucket
    ) {
        this(
                supabaseUrl,
                serviceRoleKey,
                productBucket,
                recipeBucket,
                paymentBucket,
                RestClient.builder()
        );
    }

    /**
     * Constructor para pruebas o inyección con builder personalizado de RestClient.
     */
    public SupabaseStorageService(
            String supabaseUrl,
            String serviceRoleKey,
            String productBucket,
            String recipeBucket,
            String paymentBucket,
            RestClient.Builder restClientBuilder
    ) {
        this.supabaseUrl = normalizeUrl(supabaseUrl);
        this.storageBaseUrl = this.supabaseUrl + "/storage/v1";
        this.serviceRoleKey = serviceRoleKey;
        this.productBucket = productBucket;
        this.recipeBucket = recipeBucket;
        this.paymentBucket = paymentBucket;
        this.restClient = restClientBuilder
                .baseUrl(this.storageBaseUrl)
                .defaultHeader("apikey", serviceRoleKey)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + serviceRoleKey)
                .build();
    }

    @Override
    public String upload(String bucket, String path, byte[] bytes, String contentType) {
        Objects.requireNonNull(bucket, "El bucket no puede ser nulo");
        Objects.requireNonNull(path, "La ruta no puede ser nula");
        if (bytes == null || bytes.length == 0) {
            throw new StorageException("El archivo a subir no puede estar vacío");
        }

        String cleanPath = sanitizePath(path);
        MediaType mediaType = (contentType != null && !contentType.isBlank())
                ? MediaType.parseMediaType(contentType)
                : MediaType.APPLICATION_OCTET_STREAM;

        log.debug("Subiendo archivo a Supabase Storage: bucket={}, path={}, size={} bytes, contentType={}",
                bucket, cleanPath, bytes.length, mediaType);

        try {
            restClient.post()
                    .uri("/object/" + bucket + "/" + cleanPath)
                    .contentType(mediaType)
                    .header("x-upsert", "true")
                    .body(bytes)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        String responseBody = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        log.error("Error al subir archivo a Supabase Storage: status={}, body={}",
                                response.getStatusCode(), responseBody);
                        throw new StorageException("Error subiendo archivo a Supabase Storage: "
                                + response.getStatusCode() + " - " + responseBody);
                    })
                    .toBodilessEntity();

            log.info("Archivo subido satisfactoriamente a bucket='{}', path='{}'", bucket, cleanPath);
            return cleanPath;
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            log.error("Fallo inesperado al conectar con Supabase Storage para upload: {}", e.getMessage(), e);
            throw new StorageException("Error de comunicación con Supabase Storage al subir archivo: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] download(String bucket, String path) {
        Objects.requireNonNull(bucket, "El bucket no puede ser nulo");
        Objects.requireNonNull(path, "La ruta no puede ser nula");

        String cleanPath = sanitizePath(path);
        log.debug("Descargando archivo de Supabase Storage: bucket={}, path={}", bucket, cleanPath);

        try {
            byte[] content = restClient.get()
                    .uri("/object/" + bucket + "/" + cleanPath)
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (request, response) -> {
                        throw new StorageFileNotFoundException(
                                "Archivo no encontrado en bucket '" + bucket + "': " + cleanPath);
                    })
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        throw new StorageException("Error descargando archivo de Supabase Storage: "
                                + response.getStatusCode() + " - " + body);
                    })
                    .body(byte[].class);

            if (content == null) {
                throw new StorageFileNotFoundException("Respuesta vacía al descargar " + cleanPath);
            }

            return content;
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            log.error("Fallo inesperado al descargar de Supabase Storage: {}", e.getMessage(), e);
            throw new StorageException("Error al descargar archivo: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String bucket, String path) {
        Objects.requireNonNull(bucket, "El bucket no puede ser nulo");
        Objects.requireNonNull(path, "La ruta no puede ser nula");

        String cleanPath = sanitizePath(path);
        log.debug("Eliminando archivo en Supabase Storage: bucket={}, path={}", bucket, cleanPath);

        try {
            restClient.delete()
                    .uri("/object/" + bucket + "/" + cleanPath)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        throw new StorageException("Error eliminando archivo en Supabase Storage: "
                                + response.getStatusCode() + " - " + body);
                    })
                    .toBodilessEntity();

            log.info("Archivo eliminado correctamente: bucket='{}', path='{}'", bucket, cleanPath);
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            log.error("Fallo inesperado al eliminar de Supabase Storage: {}", e.getMessage(), e);
            throw new StorageException("Error al eliminar archivo: " + e.getMessage(), e);
        }
    }

    @Override
    public String getPublicUrl(String bucket, String path) {
        Objects.requireNonNull(bucket, "El bucket no puede ser nulo");
        Objects.requireNonNull(path, "La ruta no puede ser nula");

        String cleanPath = sanitizePath(path);
        return String.format("%s/storage/v1/object/public/%s/%s", supabaseUrl, bucket, cleanPath);
    }

    @Override
    public String getSignedUrl(String bucket, String path, int expiresInSeconds) {
        Objects.requireNonNull(bucket, "El bucket no puede ser nulo");
        Objects.requireNonNull(path, "La ruta no puede ser nula");

        if (expiresInSeconds <= 0) {
            throw new IllegalArgumentException("El tiempo de expiración debe ser mayor a 0 segundos");
        }

        String cleanPath = sanitizePath(path);
        log.debug("Generando URL firmada para bucket={}, path={}, expiresIn={}s", bucket, cleanPath, expiresInSeconds);

        try {
            SignedUrlResponse response = restClient.post()
                    .uri("/object/sign/" + bucket + "/" + cleanPath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("expiresIn", expiresInSeconds))
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (request, resp) -> {
                        throw new StorageFileNotFoundException(
                                "Archivo no encontrado para firmar en bucket '" + bucket + "': " + cleanPath);
                    })
                    .onStatus(HttpStatusCode::isError, (request, resp) -> {
                        String body = new String(resp.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        throw new StorageException("Error generando URL firmada en Supabase Storage: "
                                + resp.getStatusCode() + " - " + body);
                    })
                    .body(SignedUrlResponse.class);

            if (response == null || response.signedURL() == null || response.signedURL().isBlank()) {
                throw new StorageException("Respuesta vacía o inválida al firmar URL para: " + cleanPath);
            }

            String rawSignedUrl = response.signedURL();
            if (rawSignedUrl.startsWith("http://") || rawSignedUrl.startsWith("https://")) {
                return rawSignedUrl;
            }

            if (rawSignedUrl.startsWith("/")) {
                return storageBaseUrl + rawSignedUrl;
            }

            return storageBaseUrl + "/" + rawSignedUrl;
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            log.error("Fallo inesperado al generar URL firmada: {}", e.getMessage(), e);
            throw new StorageException("Error al generar URL firmada: " + e.getMessage(), e);
        }
    }

    public String getProductBucket() {
        return productBucket;
    }

    public String getRecipeBucket() {
        return recipeBucket;
    }

    public String getPaymentBucket() {
        return paymentBucket;
    }

    private static String normalizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return "http://localhost:54321";
        }
        String trimmed = url.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private String sanitizePath(String path) {
        String clean = path.trim().replace("\\", "/");
        while (clean.startsWith("/")) {
            clean = clean.substring(1);
        }
        return clean;
    }

    public record SignedUrlResponse(@JsonProperty("signedURL") String signedURL) {
    }
}
