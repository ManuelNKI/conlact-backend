# BE-20 - Gestión de Galería en `product_images`

Documentación técnica de la implementación de la galería de fotografías de productos (quesos), basada en el catálogo de integración `CONTRATOS_BACKEND_BE14_A_BE41.md` y en el modelo relacional de la base de datos de CONLAC-T.

---

## 1. Convenciones y Seguridad

- **URL Base:** `http://localhost:8080` (Backend Spring Boot) / `http://localhost:3000` (Next.js Frontend).
- **Rutas y Soporte Dual (Español / Inglés):** Todos los endpoints admiten rutas equivalentes en español e inglés (`/imagenes` y `/images`, `/fotos` y `/photos`).
- **Autenticación y Autorización:**
  - Las consultas públicas (`/api/productos/{id}/imagenes` o `/api/products/{id}/images`) están permitidas para cualquier cliente (`permitAll()`).
  - Todas las operaciones administrativas de gestión fotográfica (`/api/admin/productos/{id}/imagenes/**` o `/api/admin/products/{id}/images/**`) exigen cabecera `Authorization: Bearer <supabase_jwt_token>` con rol verificado `admin` y perfil activo (`is_active = true`) en la tabla `profiles`.
- **Estandarización de Código:**
  - Código fuente, nombres de paquetes, clases, métodos y entidades completamente en inglés (`ProductImage`, `ProductImageService`, `ProductImageRepository`, `ProductImageCreateRequest`, `ProductImageResponse`, etc.).
  - Mensajes de validación y errores descriptivos en español para el consumidor de la API.
- **Control de Errores:**
  - `400 Bad Request`: Parámetros o cuerpo inválidos (validación Bean Validation, IDs repetidos en reordenamiento).
  - `401 Unauthorized`: Token ausente, expirado o manipulado.
  - `403 Forbidden`: Token válido pero sin rol administrativo.
  - `404 Not Found`: Producto o imagen no encontrada (o imagen perteneciente a otro producto).
  - `409 Conflict`: Intento de asociar un `storage_path` duplicado al mismo producto.

---

## 2. Catálogo de Endpoints (BE-20)

| Método | Ruta en Español | Alias en Inglés | Seguridad | Código Éxito | Descripción |
|---|---|---|---|---|---|
| **POST** | `/api/admin/productos/{id}/imagenes` | `/api/admin/products/{id}/images` | Admin (`ROLE_ADMIN`) | `201 Created` | Asocia una nueva fotografía a la galería del queso |
| **GET** | `/api/admin/productos/{id}/imagenes` | `/api/admin/products/{id}/images` | Admin (`ROLE_ADMIN`) | `200 OK` | Obtiene el listado completo de fotos de la galería (admin) |
| **GET** | `/api/productos/{id}/imagenes` | `/api/products/{id}/images` | Pública (`permitAll`) | `200 OK` | Consulta pública de fotografías para catálogo/tienda |
| **PATCH** | `/api/admin/productos/{id}/imagenes/{imageId}/principal` | `/api/admin/products/{id}/images/{imageId}/primary` | Admin (`ROLE_ADMIN`) | `200 OK` | Fija una foto como imagen principal/portada |
| **PUT** | `/api/admin/productos/{id}/imagenes/{imageId}/principal` | `/api/admin/products/{id}/images/{imageId}/primary` | Admin (`ROLE_ADMIN`) | `200 OK` | Alias idempotente para fijar imagen principal |
| **PUT** | `/api/admin/productos/{id}/imagenes/orden` | `/api/admin/products/{id}/images/reorder` | Admin (`ROLE_ADMIN`) | `200 OK` | Reordena atómicamente la galería de fotos |
| **DELETE** | `/api/admin/productos/{id}/imagenes/{imageId}` | `/api/admin/products/{id}/images/{imageId}` | Admin (`ROLE_ADMIN`) | `204 No Content` | Elimina la foto de la BD y borra el archivo físico en Storage |

---

## 3. Especificación de Cargas Útiles (Payloads)

### A. Agregar Imagen a la Galería (`POST`)
- **Headers:** `Authorization: Bearer <token>`, `Content-Type: application/json`
- **Request Body:**
```json
{
  "storage_path": "products/prod-queso-fresco/frontal.webp",
  "alt_text": "Fotografía frontal del Queso Fresco El Lindero",
  "sort_order": 0,
  "is_primary": true
}
```
- **Campos:**
  - `storage_path` *(obligatorio, máx 1024)*: Ruta del objeto en el bucket `product-images` de Supabase Storage.
  - `alt_text` *(opcional, máx 255)*: Texto descriptivo de accesibilidad.
  - `sort_order` *(opcional, entero >= 0)*: Posición de visualización. Si se omite, se asigna automáticamente al final.
  - `is_primary` *(opcional, booleano)*: Si es `true` (o `sort_order == 0`, o si es la primera imagen del producto), se establece en `sort_order = 0` y se desplazan automáticamente las imágenes previas para garantizar una única portada.

- **Response Body (`201 Created`):**
```json
{
  "id": "e2a149b8-1549-411a-8289-4b68dcb80782",
  "product_id": "a0000000-0000-0000-0000-000000000001",
  "storage_path": "products/prod-queso-fresco/frontal.webp",
  "url": "https://<supabase-project>.supabase.co/storage/v1/object/public/product-images/products/prod-queso-fresco/frontal.webp",
  "alt_text": "Fotografía frontal del Queso Fresco El Lindero",
  "sort_order": 0,
  "is_primary": true,
  "created_at": "2026-10-04T18:10:00Z"
}
```

---

### B. Listado de Galería (`GET`)
Retorna la colección ordenada por `sort_order ASC, id ASC`:
```json
[
  {
    "id": "e2a149b8-1549-411a-8289-4b68dcb80782",
    "product_id": "a0000000-0000-0000-0000-000000000001",
    "storage_path": "products/prod-queso-fresco/frontal.webp",
    "url": "https://.../frontal.webp",
    "alt_text": "Fotografía frontal del Queso Fresco El Lindero",
    "sort_order": 0,
    "is_primary": true,
    "created_at": "2026-10-04T18:10:00Z"
  },
  {
    "id": "b48f9321-789a-4cde-8012-123456789abc",
    "product_id": "a0000000-0000-0000-0000-000000000001",
    "storage_path": "products/prod-queso-fresco/corte.webp",
    "url": "https://.../corte.webp",
    "alt_text": "Corte transversal con textura de masa",
    "sort_order": 1,
    "is_primary": false,
    "created_at": "2026-10-04T18:12:00Z"
  }
]
```

---

### C. Establecer Portada / Imagen Principal (`PATCH / PUT .../principal`)
- No requiere body (o body opcional).
- Fija `sort_order = 0` en la foto especificada.
- Reorganiza consecutivamente las demás fotos existentes (`1, 2, 3...`) para evitar colisiones.
- **Response (`200 OK`):** Devuelve la entidad de la imagen actualizada con `is_primary: true` y `sort_order: 0`.

---

### D. Reordenamiento Atómico de Galería (`PUT .../orden`)
- **Request Body:**
```json
{
  "images": [
    {
      "id": "b48f9321-789a-4cde-8012-123456789abc",
      "sort_order": 0
    },
    {
      "id": "e2a149b8-1549-411a-8289-4b68dcb80782",
      "sort_order": 1
    }
  ]
}
```
- **Validaciones:**
  - Valida que la lista no esté vacía.
  - Rechaza IDs duplicados en el payload (`400 Bad Request`).
  - Rechaza cualquier imagen que no pertenezca al producto indicado (`404 Not Found`).
  - La actualización se realiza de manera transaccional (`@Transactional`).

---

### E. Eliminación y Limpieza en Cascada (`DELETE`)
1. **Borrado Individual (`DELETE /api/admin/productos/{id}/imagenes/{imageId}`):**
   - Elimina el registro de la tabla `product_images`.
   - Limpia de forma segura el archivo físico en el bucket de Supabase Storage (`storage.delete(bucket, path)`).
   - Si la foto eliminada era la portada (`sort_order = 0`), promueve automáticamente la siguiente foto disponible al índice 0.
   - Retorna `204 No Content`.
2. **Eliminación en Cascada de Imágenes Huérfanas:**
   - La base de datos aplica `references public.products(id) on delete cascade`.
   - La entidad `Product` mantiene `@OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)`.
   - El servicio `ProductImageService` incluye `deleteProductImagesByProduct(UUID productId)` para purgar masivamente tanto filas de BD como archivos físicos en Supabase Storage cuando se descarta un producto.

---

## 4. Pruebas Automatizadas

La implementación cuenta con 100% de cobertura en:
- **Pruebas Unitarias de Servicio (`ProductImageServiceTest`):** 10 casos de prueba para adición, desplazamiento, detección de duplicados, reordenamiento, validación de IDs y borrado.
- **Pruebas de Controlador (`AdminProductImageControllerTest`):** 8 casos de prueba para contratos REST, aliases en inglés/español y errores de validación.
- **Pruebas de Seguridad (`AdminEndpointSecurityTest`):** Verificación de rechazo anónimo (401) y no autorizado (403) sobre endpoints de imágenes.
- **Pruebas de Integración con Testcontainers (`ProductImageIntegrationTest`):** Prueba end-to-end completa con base de datos PostgreSQL real efímera verificando adición, cambio de portada, consulta pública, reordenamiento, borrado individual y cascada huérfana tras eliminar el producto padre.
