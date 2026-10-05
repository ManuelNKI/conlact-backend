# [BE-16] QA S3: Colección Postman, Tests Unitarios y Pruebas de Integración

## 1. Resumen Ejecutivo
En el marco de la Semana 3 (S3), se diseñó e implementó la suite completa de aseguramiento de calidad (QA) para los dominios de **Asociaciones de Productores** y **Testimonios de Clientes**. Se cubren tanto endpoints públicos de consulta como endpoints de administración protegidos con autenticación JWT Bearer y control de roles (`ROLE_ADMIN`).

---

## 2. Alcance Técnico
- **Frameworks de prueba**: JUnit 5 (Jupiter), Mockito (`@Mock`, `@InjectMocks`, `@MockitoBean`), Spring MockMvc, RestClient para pruebas de integración contra PostgreSQL real (Testcontainers).
- **Rutas duales cubiertas**: Soporte simultáneo para rutas en español (`/api/testimonios`, `/api/asociaciones`) y sus alias internacionales en inglés (`/api/testimonials`, `/api/associations`).
- **Validaciones automatizadas**: Verificación de códigos de estado HTTP (`200 OK`, `201 CREATED`, `204 NO CONTENT`, `400 BAD REQUEST`, `404 NOT FOUND`), serialización Jackson Snake_case, schemas JSON y preservación de slug.

---

## 3. Matriz de Pruebas Automatizadas

| Clase de Prueba | Tipo | Casos Cubiertos | Resultado |
|---|---|---|---|
| [`TestimonialControllerTest`](file:///d:/2Proyectos/CONLACT/conlact-backend/src/test/java/com/conlact/conlact_backend/controller/TestimonialControllerTest.java) | Unitario (MockMvc) | `GET /api/testimonios` y alias `GET /api/testimonials` retornan lista filtrada (`is_published=true`), validación de schema y atributos (`nombre_autor`, `comentario`, `calificacion`). | **PASS** |
| [`AdminTestimonialControllerTest`](file:///d:/2Proyectos/CONLACT/conlact-backend/src/test/java/com/conlact/conlact_backend/controller/AdminTestimonialControllerTest.java) | Unitario (MockMvc) | `GET /api/admin/testimonios`, `POST` creación con validación Jakarta Bean Validation (`@NotBlank`, `@Min(1)`, `@Max(5)`), `PATCH /{id}/publicar`, `DELETE /{id}` con retorno 204. Rechazo 400 ante payloads inválidos. | **PASS** |
| [`AssociationIntegrationTest`](file:///d:/2Proyectos/CONLACT/conlact-backend/src/test/java/com/conlact/conlact_backend/AssociationIntegrationTest.java) | Integración (PostgreSQL) | Listado público en BD sembrada, consulta por slug `queseras-pilahuin`, validación 404 para slugs desconocidos, ciclo de vida administrativo con token JWT Bearer. | **PASS** |

---

## 4. Colección Postman Versionada
- **Archivo**: [`conlact-backend/docs/postman/BE16_S3_associations_testimonials.postman_collection.json`](file:///d:/2Proyectos/CONLACT/conlact-backend/docs/postman/BE16_S3_associations_testimonials.postman_collection.json)
- **Variables de Entorno**:
  - `{{base_url}}`: URL base del backend (por defecto: `http://localhost:8080`).
  - `{{admin_token}}`: Token Bearer JWT de administrador para Supabase Auth.
  - `{{association_slug}}`: `queseras-pilahuin`.
  - `{{association_id}}`: `a0000000-0000-0000-0000-000000000001`.
  - `{{testimonial_id}}`: Almacenado dinámicamente durante la ejecución del runner.

### Pruebas Postman Automatizadas Incluidas
1. **Listar asociaciones públicas [ES]**: Valida código `200 OK`, respuesta tipo array con longitud > 0, propiedades `id`, `nombre`, `slug`.
2. **Listar asociaciones públicas [EN Alias]**: Valida código `200 OK` en ruta alternativa.
3. **Obtener asociación por slug sembrado**: Valida código `200 OK`, `slug === "queseras-pilahuin"`, parroquia y nombre.
4. **Consulta asociación inexistente**: Valida código `404 NOT FOUND`.
5. **Listar testimonios públicos [ES / EN]**: Valida código `200 OK` y atributos de autor.
6. **Crear testimonio admin**: Valida código `201 CREATED`, guarda `testimonial_id` en variable de entorno.
7. **Alternar publicación**: Valida código `200 OK` y presencia de bandera `es_publicado`.
8. **Eliminar testimonio admin**: Valida código `204 NO CONTENT`.

---

## 5. Comando de Ejecución
```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.4.1'
.\mvnw.cmd test -Dtest=TestimonialControllerTest,AdminTestimonialControllerTest,AssociationIntegrationTest
```
