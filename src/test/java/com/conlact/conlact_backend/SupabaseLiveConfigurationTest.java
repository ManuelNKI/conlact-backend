package com.conlact.conlact_backend;

import com.conlact.conlact_backend.config.DotenvEnvironmentPostProcessor;
import com.conlact.conlact_backend.security.JwtTokenProvider;
import com.conlact.conlact_backend.storage.SupabaseStorageService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;

import static org.assertj.core.api.Assertions.assertThat;

class SupabaseLiveConfigurationTest {

    private static StandardEnvironment environment;
    private static SupabaseStorageService storageService;
    private static JwtTokenProvider jwtTokenProvider;

    @BeforeAll
    static void setUp() {
        environment = new StandardEnvironment();
        DotenvEnvironmentPostProcessor postProcessor = new DotenvEnvironmentPostProcessor();
        postProcessor.postProcessEnvironment(environment, null);

        String supabaseUrl = environment.getProperty("SUPABASE_URL");
        String serviceRoleKey = environment.getProperty("SUPABASE_SERVICE_ROLE_KEY");

        // Omitir ejecución limpia si no existen credenciales reales en el entorno (ej: CI/CD sin .env)
        Assumptions.assumeTrue(
                supabaseUrl != null && supabaseUrl.startsWith("https://") &&
                        serviceRoleKey != null && !serviceRoleKey.isBlank() && !serviceRoleKey.contains("super-secret"),
                "Prueba en vivo omitida: SUPABASE_URL y SUPABASE_SERVICE_ROLE_KEY reales no configuradas en .env"
        );

        String jwtPublicKey = environment.getProperty("SUPABASE_JWT_PUBLIC_KEY");
        String jwtIssuer = environment.getProperty("SUPABASE_JWT_ISSUER");
        String productBucket = environment.getProperty("STORAGE_PRODUCT_BUCKET", "product-images");
        String recipeBucket = environment.getProperty("STORAGE_RECIPE_BUCKET", "recipe-images");
        String paymentBucket = environment.getProperty("STORAGE_PAYMENT_BUCKET", "payment-proofs");

        storageService = new SupabaseStorageService(
                supabaseUrl,
                serviceRoleKey,
                productBucket,
                recipeBucket,
                paymentBucket
        );

        jwtTokenProvider = new JwtTokenProvider(
                supabaseUrl,
                jwtPublicKey,
                "",
                jwtIssuer
        );
    }

    @Test
    @DisplayName("Supabase Storage Live: Debe subir, obtener URL, descargar y eliminar archivo en product-images")
    void testLiveStorageOperations() {
        String testPath = "test/connectivity-check.webp";
        byte[] testBytes = "RIFF\u0000\u0000\u0000\u0000WEBPVP8 \u0000\u0000\u0000\u0000".getBytes(StandardCharsets.ISO_8859_1);

        // 1. Subir con un MIME type permitido (image/webp)
        String uploadedPath = storageService.upload("product-images", testPath, testBytes, "image/webp");
        assertThat(uploadedPath).isEqualTo(testPath);

        // 2. Obtener URL pública
        String publicUrl = storageService.getPublicUrl("product-images", testPath);
        assertThat(publicUrl).contains("product-images").contains(testPath);

        // 3. Descargar
        byte[] downloaded = storageService.download("product-images", testPath);
        assertThat(downloaded).isEqualTo(testBytes);

        // 4. Obtener URL firmada
        String signedUrl = storageService.getSignedUrl("product-images", testPath, 60);
        assertThat(signedUrl).startsWith("https://");

        // 5. Eliminar
        storageService.delete("product-images", testPath);
    }

    @Test
    @DisplayName("Supabase JWT Live: Debe inicializar la clave pública ECC (P-256) desde Supabase")
    void testLiveJwtConfiguration() {
        PublicKey key = (PublicKey) jwtTokenProvider.getVerificationKey();
        assertThat(key).isNotNull();
        assertThat(key.getAlgorithm()).isEqualTo("EC");
        assertThat(key).isInstanceOf(ECPublicKey.class);

        ECPublicKey ecKey = (ECPublicKey) key;
        // P-256 curve bit length is 256
        assertThat(ecKey.getParams().getCurve().getField().getFieldSize()).isEqualTo(256);
    }
}
