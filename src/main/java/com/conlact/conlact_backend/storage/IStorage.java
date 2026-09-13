package com.conlact.conlact_backend.storage;

/**
 * Contrato de almacenamiento agnóstico para CONLAC-T.
 * Desacopla la lógica de negocio del proveedor de almacenamiento en la nube (Supabase Storage).
 */
public interface IStorage {

    /**
     * Sube un archivo binario al bucket y ruta indicados.
     *
     * @param bucket      Identificador del bucket (product-images, recipe-images, payment-proofs).
     * @param path        Ruta relativa y nombre del archivo dentro del bucket.
     * @param bytes       Contenido binario del archivo.
     * @param contentType Tipo MIME (ej: image/webp, image/jpeg, application/pdf).
     * @return La ruta relativa almacenada del archivo.
     */
    String upload(String bucket, String path, byte[] bytes, String contentType);

    /**
     * Descarga el contenido binario de un archivo almacenado.
     *
     * @param bucket Identificador del bucket.
     * @param path   Ruta relativa del archivo dentro del bucket.
     * @return Bytes del archivo.
     */
    byte[] download(String bucket, String path);

    /**
     * Elimina un archivo del bucket.
     *
     * @param bucket Identificador del bucket.
     * @param path   Ruta relativa del archivo dentro del bucket.
     */
    void delete(String bucket, String path);

    /**
     * Genera la URL pública de acceso directo para archivos en buckets con acceso público.
     *
     * @param bucket Identificador del bucket público.
     * @param path   Ruta relativa del archivo dentro del bucket.
     * @return URL pública completa de CDN/acceso directo.
     */
    String getPublicUrl(String bucket, String path);

    /**
     * Genera una URL firmada de acceso temporal para archivos en buckets privados (ej: comprobantes de pago).
     *
     * @param bucket          Identificador del bucket privado.
     * @param path            Ruta relativa del archivo.
     * @param expiresInSeconds Tiempo de validez del enlace firmado en segundos.
     * @return URL temporal firmada para descarga segura.
     */
    String getSignedUrl(String bucket, String path, int expiresInSeconds);
}
