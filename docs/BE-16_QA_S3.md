# [BE-16] QA S3: Colección Postman, Tests Unitarios y Pruebas de Integración

## 1. Resumen Ejecutivo
En el marco de la Semana 3 (S3), se diseñó e implementó la suite completa de aseguramiento de calidad (QA) para los dominios de **Asociaciones de Productores** (cubriendo BE-12, BE-13 y BE-14) y **Testimonios de Clientes** (BE-15). Se cubren tanto endpoints públicos de consulta como endpoints de administración protegidos con autenticación JWT Bearer y control de roles (`ROLE_ADMIN`).

---

## 2. Alcance Técnico
- **Frameworks de prueba**: JUnit 5 (Jupiter), Mockito (`@Mock`, `@InjectMocks`, `@MockitoBean`), Spring MockMvc, RestClient para pruebas de integración contra PostgreSQL real (Testcontainers).
- **Rutas duales cubiertas**: Soporte simultáneo para rutas en español (`/api/testimonios`, `/api/asociaciones`) y sus alias internacionales en inglés (`/api/testimonials`, `/api/associations`).
- **Validaciones automatizadas**: Verificación de códigos de estado HTTP (`200 OK`, `201 CREATED`, `204 NO CONTENT`, `400 BAD REQUEST`, `404 NOT FOUND`), serialización Jackson Snake_case, schemas JSON y preservación de slug.
- **Gestión de Fotos [BE-14]**: Comprobación del ciclo de vida y persistencia del arreglo de URLs de fotos (`fotos`) en filiales comunitarias.

---

## 3. Matriz de Pruebas Automatizadas

| Clase de Prueba | Tipo | Casos Cubiertos | Resultado |
|---|---|---|---|
| [`TestimonialControllerTest`](file:///d:/2Proyectos/CONLACT/conlact-backend/src/test/java/com/conlact/conlact_backend/controller/TestimonialControllerTest.java) | Unitario (MockMvc) | `GET /api/testimonios` y alias `GET /api/testimonials` retornan lista filtrada (`is_published=true`), validación de schema y atributos (`autor_nombre`, `frase`, `autor_tipo`). | **PASS** |
| [`AdminTestimonialControllerTest`](file:///d:/2Proyectos/CONLACT/conlact-backend/src/test/java/com/conlact/conlact_backend/controller/AdminTestimonialControllerTest.java) | Unitario (MockMvc) | `GET /api/admin/testimonios`, `POST` creación con validación Jakarta Bean Validation (`@NotBlank`), `PATCH /{id}/publicar`, `DELETE /{id}` con retorno 204. Rechazo 400 ante payloads inválidos. | **PASS** |
| [`TestimonialIntegrationTest`](file:///d:/2Proyectos/CONLACT/conlact-backend/src/test/java/com/conlact/conlact_backend/TestimonialIntegrationTest.java) | Integración (PostgreSQL) | Consulta de testimonios sembrados en rutas duales (`/api/testimonios` y `/api/testimonials`). Ciclo de vida administrativo completo (creación no publicada -> verificación de ausencia pública -> publicación vía PATCH -> presencia pública -> eliminación DELETE -> verificación en BD). Validación de rechazo 400. | **PASS** |
| [`AssociationIntegrationTest`](file:///d:/2Proyectos/CONLACT/conlact-backend/src/test/java/com/conlact/conlact_backend/AssociationIntegrationTest.java) | Integración (PostgreSQL) | Listado público en BD sembrada, consulta por slug `asociacion-el-lindero`, validación 404 para slugs desconocidos, ciclo de vida administrativo con token JWT Bearer y persistencia de URLs de fotografías (`fotos` - BE-14). | **PASS** |

---

## 4. Colección Postman Versionada
- **Archivo**: [`conlact-backend/docs/postman/BE16_S3_associations_testimonials.postman_collection.json`](file:///d:/2Proyectos/CONLACT/conlact-backend/docs/postman/BE16_S3_associations_testimonials.postman_collection.json)
- **Variables de Entorno**:
  - `{{base_url}}`: URL base del backend (por defecto: `http://localhost:8080`).
  - `{{admin_token}}`: Token Bearer JWT de administrador para Supabase Auth.
  - `{{association_slug}}`: `asociacion-el-lindero`.
  - `{{association_id}}`: `a0000000-0000-0000-0000-000000000001`.
  - `{{testimonial_id}}`: `e0000000-0000-0000-0000-000000000001`.

### Pruebas Postman Automatizadas Incluidas
1. **Listar asociaciones públicas [ES]**: Valida código `200 OK`, respuesta tipo array con longitud > 0, propiedades `id`, `nombre`, `slug`.
2. **Listar asociaciones públicas [EN Alias]**: Valida código `200 OK` en ruta alternativa.
3. **Obtener asociación por slug sembrado**: Valida código `200 OK`, `slug === "asociacion-el-lindero"`, nombre y ubicación.
4. **Consulta asociación inexistente**: Valida código `404 NOT FOUND`.
5. **Actualizar fotos de asociación [BE-14]**: `PUT /api/admin/asociaciones/{{association_id}}` con fotos en formato arreglo.
6. **Listar testimonios públicos [ES / EN]**: Valida código `200 OK` y atributos `autor_nombre` y `frase`.
7. **Crear testimonio admin**: Valida código `201 CREATED`, guarda `testimonial_id` en variable de entorno.
8. **Alternar publicación**: Valida código `200 OK` y presencia de bandera `is_published`.
9. **Eliminar testimonio admin**: Valida código `204 NO CONTENT`.

---

## 5. Comando de Ejecución
```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.4.1'
.\mvnw.cmd test -Dtest=TestimonialControllerTest,AdminTestimonialControllerTest,TestimonialIntegrationTest,AssociationIntegrationTest
```
