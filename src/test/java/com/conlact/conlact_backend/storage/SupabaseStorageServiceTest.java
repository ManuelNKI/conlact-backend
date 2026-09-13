package com.conlact.conlact_backend.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class SupabaseStorageServiceTest {

    private static final String SUPABASE_URL = "https://example.supabase.co";
    private static final String SERVICE_KEY = "test-service-role-key-123456";
    private static final String PRODUCT_BUCKET = "product-images";
    private static final String RECIPE_BUCKET = "recipe-images";
    private static final String PAYMENT_BUCKET = "payment-proofs";

    private MockRestServiceServer server;
    private SupabaseStorageService storageService;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();

        storageService = new SupabaseStorageService(
                SUPABASE_URL,
                SERVICE_KEY,
                PRODUCT_BUCKET,
                RECIPE_BUCKET,
                PAYMENT_BUCKET,
                restClientBuilder
        );
    }

    @Test
    @DisplayName("upload() debe enviar petición POST con encabezados de autenticación, x-upsert y payload binario")
    void shouldUploadFileSuccessfully() {
        byte[] payload = "mock image content".getBytes(StandardCharsets.UTF_8);

        server.expect(requestTo("https://example.supabase.co/storage/v1/object/product-images/products/queso.webp"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("apikey", SERVICE_KEY))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + SERVICE_KEY))
                .andExpect(header("x-upsert", "true"))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, "image/webp"))
                .andExpect(content().bytes(payload))
                .andRespond(withSuccess("{\"Key\":\"product-images/products/queso.webp\"}", MediaType.APPLICATION_JSON));

        String resultPath = storageService.upload(PRODUCT_BUCKET, "products/queso.webp", payload, "image/webp");

        assertThat(resultPath).isEqualTo("products/queso.webp");
        server.verify();
    }

    @Test
    @DisplayName("upload() con ruta que inicia con '/' debe sanear la ruta correctamente")
    void shouldUploadFileWithLeadingSlashSanitized() {
        byte[] payload = "dummy bytes".getBytes(StandardCharsets.UTF_8);

        server.expect(requestTo("https://example.supabase.co/storage/v1/object/recipe-images/recipes/locro.jpg"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"Key\":\"recipe-images/recipes/locro.jpg\"}", MediaType.APPLICATION_JSON));

        String resultPath = storageService.upload(RECIPE_BUCKET, "/recipes/locro.jpg", payload, "image/jpeg");

        assertThat(resultPath).isEqualTo("recipes/locro.jpg");
        server.verify();
    }

    @Test
    @DisplayName("upload() debe lanzar StorageException si el archivo está vacío")
    void shouldThrowExceptionWhenUploadingEmptyContent() {
        assertThatThrownBy(() -> storageService.upload(PRODUCT_BUCKET, "test.png", new byte[0], "image/png"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("no puede estar vacío");
    }

    @Test
    @DisplayName("upload() debe lanzar StorageException cuando Supabase Storage retorna error HTTP 500")
    void shouldThrowExceptionWhenSupabaseReturnsErrorOnUpload() {
        byte[] payload = "test data".getBytes(StandardCharsets.UTF_8);

        server.expect(requestTo("https://example.supabase.co/storage/v1/object/product-images/error.jpg"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError().body("Storage internal error"));

        assertThatThrownBy(() -> storageService.upload(PRODUCT_BUCKET, "error.jpg", payload, "image/jpeg"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Error subiendo archivo");

        server.verify();
    }

    @Test
    @DisplayName("download() debe retornar los bytes del archivo cuando la respuesta es 200 OK")
    void shouldDownloadFileSuccessfully() {
        byte[] expectedBytes = "downloaded recipe picture".getBytes(StandardCharsets.UTF_8);

        server.expect(requestTo("https://example.supabase.co/storage/v1/object/recipe-images/humitas.png"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("apikey", SERVICE_KEY))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + SERVICE_KEY))
                .andRespond(withSuccess(expectedBytes, MediaType.IMAGE_PNG));

        byte[] result = storageService.download(RECIPE_BUCKET, "humitas.png");

        assertThat(result).isEqualTo(expectedBytes);
        server.verify();
    }

    @Test
    @DisplayName("download() debe lanzar StorageFileNotFoundException cuando el archivo no existe (404)")
    void shouldThrowStorageFileNotFoundExceptionWhenResourceNotFound() {
        server.expect(requestTo("https://example.supabase.co/storage/v1/object/product-images/not-found.jpg"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("The resource was not found"));

        assertThatThrownBy(() -> storageService.download(PRODUCT_BUCKET, "not-found.jpg"))
                .isInstanceOf(StorageFileNotFoundException.class)
                .hasMessageContaining("Archivo no encontrado");

        server.verify();
    }

    @Test
    @DisplayName("delete() debe enviar petición DELETE a Supabase Storage con headers requeridos")
    void shouldDeleteFileSuccessfully() {
        server.expect(requestTo("https://example.supabase.co/storage/v1/object/payment-proofs/vouchers/voucher-1001.pdf"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("apikey", SERVICE_KEY))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + SERVICE_KEY))
                .andRespond(withSuccess());

        storageService.delete(PAYMENT_BUCKET, "vouchers/voucher-1001.pdf");

        server.verify();
    }

    @Test
    @DisplayName("getPublicUrl() debe formatear la URL pública sin peticiones de red")
    void shouldFormatPublicUrlCorrectly() {
        String publicUrl = storageService.getPublicUrl(PRODUCT_BUCKET, "products/queso-de-hoja.webp");

        assertThat(publicUrl)
                .isEqualTo("https://example.supabase.co/storage/v1/object/public/product-images/products/queso-de-hoja.webp");
    }

    @Test
    @DisplayName("getSignedUrl() debe solicitar la firma a Supabase Storage y concatenar la URL absoluta")
    void shouldGenerateSignedUrlSuccessfully() {
        String mockResponse = "{\"signedURL\":\"/object/sign/payment-proofs/proof-999.jpg?token=secret-token-xyz\"}";

        server.expect(requestTo("https://example.supabase.co/storage/v1/object/sign/payment-proofs/proof-999.jpg"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("apikey", SERVICE_KEY))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + SERVICE_KEY))
                .andExpect(content().json("{\"expiresIn\": 3600}"))
                .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

        String signedUrl = storageService.getSignedUrl(PAYMENT_BUCKET, "proof-999.jpg", 3600);

        assertThat(signedUrl).isEqualTo(
                "https://example.supabase.co/storage/v1/object/sign/payment-proofs/proof-999.jpg?token=secret-token-xyz"
        );
        server.verify();
    }

    @Test
    @DisplayName("getSignedUrl() debe lanzar StorageFileNotFoundException cuando el archivo no existe (404)")
    void shouldThrowExceptionWhenSigningNonExistentFile() {
        server.expect(requestTo("https://example.supabase.co/storage/v1/object/sign/payment-proofs/missing.pdf"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("Object not found"));

        assertThatThrownBy(() -> storageService.getSignedUrl(PAYMENT_BUCKET, "missing.pdf", 60))
                .isInstanceOf(StorageFileNotFoundException.class)
                .hasMessageContaining("Archivo no encontrado para firmar");

        server.verify();
    }

    @Test
    @DisplayName("Verificar resolución de nombres de buckets configurados")
    void shouldProvideConfiguredBucketNames() {
        assertThat(storageService.getProductBucket()).isEqualTo("product-images");
        assertThat(storageService.getRecipeBucket()).isEqualTo("recipe-images");
        assertThat(storageService.getPaymentBucket()).isEqualTo("payment-proofs");
    }
}
