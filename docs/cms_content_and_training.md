# Entrega Backend: CMS, Contenidos Editoriales, Capacitación y Contacto

> **Módulo:** CMS, Contenidos, Capacitación y Contacto  
> **Rama:** `feature/cms-content-training-contact`  
> **Estado:** Implementado, validado e integrado con Frontend Next.js  

---

## 1. Alcance y Base Reutilizada

En cumplimiento con los requerimientos técnicos de la división de arquitectura backend, se homologó, robusteció e integró el subsistema editorial y de interacción pública:

- **CRUD de Asociaciones y Galerías:** Reutilización y ampliación del modelo de entidades para preservar campos de ficha técnica extendida (`parroquia`, `socios`, `altitud_msnm`, `familias`, `horario_atencion`, `registro_bpm`, `campos_ficha`) y referencias al renombrar slugs.
- **CRUD de Recetas y Sincronización Transaccional por Lotes:** Reutilización de recetas con ingredientes, pasos, imagen, tiempo, porciones, sumando `video_url`, `tipo_queso`, `gradient`, `orden` y sincronización atómica con productos recomendados (`/api/admin/recetas/batch`).
- **CRUD de Turismo:** Extensión de atractivos turísticos con atributos de dominio editorial: `foto_url` (mapeada a `imageUrl`), `duracion`, `dificultad`, `video_url`, y endpoints administrativos (`/api/admin/turismo`).
- **Moderación de Testimonios:** Sincronización editorial en portadas y aumento del límite de longitud de autor/rol a 300 caracteres para evitar desbordamientos de datos de testimonios.
- **Páginas Editoriales persistidas en PostgreSQL:** Normalización en la tabla `page_contents` sustituyendo accesos locales/SQLite, con allowlist estricta de slugs y control de concurrencia optimista (`version` con respuesta `409 Conflict`).
- **Capacitación Normalizada:** Migración del almacenamiento de JSON crudo a tablas relacionales dedicadas (`capacitacion_secciones` y `capacitacion_contenidos`), con mutaciones atómicas de orden (`mover: "arriba" | "abajo"`), soporte para borradores y filtro estricto en lecturas públicas (únicamente contenidos publicados).
- **Contacto con Consentimiento y Anti-Spam:** Validación estricta de consentimiento obligatorio (`privacy_accepted`), validación de email RFC y trampa honeypot (`hp_field`) silenciosa para mitigación de bots.

---

## 2. Actividades Desarrolladas y Criterios Técnicos

### 2.1 Homologación de DTOs y Reglas de Negocio
Se homologaron los contratos y respuestas JSON con los payloads esperados por el frontend:
- **Páginas Editoriales:**
  - `GET /api/paginas/{slug}`: Retorna `PageContentResponse` público con fallback por defecto estructurado según el slug solicitado.
  - `GET /api/admin/paginas/{slug}`: Retorna contenido editorial incluyendo metadatos de auditoría y versión concurrente.
  - `POST /api/admin/paginas/{slug}`: Valida el slug contra la allowlist oficial (`home`, `tienda`, `asociaciones`, `turismo`, `nosotros`, `como-comprar`, `contacto`, `footer`, `recetas`, `asoc-{slug}`). Valida el número de versión enviada contra el registro actual en base de datos; si difiere, retorna `409 Conflict`.
- **Turismo:**
  - Mapeo de `photoUrl` a partir de `imageUrl` en `TouristAttractionResponse` (corrigiendo mapeo previo a `null`), además de `duracion`, `dificultad` y `video_url`.
- **Recetas:**
  - `RecipeResponse` incluye `video_url`, `tipo_queso`, `gradient`, `orden` y lista de `productos_recomendados` (`id`, `nombre`, `slug`, `precio`).
  - `POST /api/admin/recetas/batch`: Lote atómico `@Transactional` que valida, actualiza la configuración de página `recetas` y sincroniza las recetas activas con eliminaciones explícitas de las ausentes en la carga masiva.

### 2.2 Preservación de Campos Extendidos de Asociaciones
- Entidad `Association` ampliada con:
  - `parish` (`parroquia`)
  - `members` (`socios`)
  - `altitudeMsnm` (`altitud_msnm`)
  - `numberOfFamilies` (`familias`)
  - `businessHours` (`horario_atencion`)
  - `bpmRegistration` (`registro_bpm`)
  - `fieldsJson` (`campos_ficha`)
- En `AssociationService.updateAssociation`, todos los campos extendidos se conservan y mapean adecuadamente. Al modificar un slug, se preservan intactas las asociaciones de productos, recetas y configuraciones editoriales asociadas (`asoc-{slug}`).

### 2.3 Capacitación: Normalización Relacional y Reordenamiento Atómico
- Se crearon las tablas `capacitacion_secciones` y `capacitacion_contenidos` con clave foránea en cascada (`ON DELETE CASCADE`).
- **Filtrado Público vs. Privado:**
  - `GET /api/capacitacion`: Retorna únicamente secciones con `publicado = true` y contenidos internos con `publicado = true`, ordenados estrictamente por `orden ASC`.
  - `GET /api/admin/secciones`: Retorna la totalidad de secciones (incluyendo borradores `publicado = false`), con conteo de materiales y estructura completa.
- **Mutación Atómica del Orden:**
  - `PATCH /api/admin/secciones/{id}` con `{ "mover": "arriba" }` o `{ "mover": "abajo" }` intercambia de forma transaccional el valor de `orden` con la sección inmediatamente adyacente.
  - `PATCH /api/admin/contenidos/{id}` con `{ "mover": "arriba" }` o `{ "mover": "abajo" }` realiza el swap atómico dentro de la misma sección.
- **Validación de Tipo de Contenido:**
  - Contenido `texto`: requiere campo `contenido` obligatorio.
  - Contenido `video`, `enlace`, `documento`: requiere campo `url` obligatorio con formato de URL válido.

### 2.4 Contacto: Consentimiento Obligatorio y Trampa Anti-Spam
- `ContactMessageCreateRequest` validado con `@AssertTrue` en `privacyAccepted`:
  - Retorna `400 Bad Request` si no se acepta expresamente la política de privacidad.
  - Límite de correo electrónico ajustado a 254 caracteres según RFC 5321.
- Detección de Bots con Honeypot:
  - El payload acepta el campo opcional oculto `hp_field`. Si un bot rellena dicho campo, el servicio simula éxito retornando `201 Created` / `received`, pero descarta silenciosamente la persistencia en base de datos y la notificación por correo SMTP, mitigando saturación de spam.

---

## 3. Matriz de Endpoints Implementados

| Módulo | Método | Endpoint | Acceso | Descripción |
|---|---|---|---|---|
| **CMS** | `GET` | `/api/paginas/{slug}` | Público | Obtener contenido editorial público de página por slug |
| **CMS** | `GET` | `/api/admin/paginas/{slug}` | ADMIN / OPERADOR | Obtener configuración editorial con metadatos y versión |
| **CMS** | `POST` | `/api/admin/paginas/{slug}` | ADMIN / OPERADOR | Guardar configuración con control de versión optimista (`409 Conflict`) |
| **Capacitación** | `GET` | `/api/capacitacion` | Público | Secciones y contenidos publicados ordenados jerárquicamente |
| **Capacitación** | `GET` | `/api/admin/secciones` | ADMIN / OPERADOR | Listado completo de secciones administrativas con borradores |
| **Capacitación** | `POST` | `/api/admin/secciones` | ADMIN / OPERADOR | Crear nueva sección de capacitación |
| **Capacitación** | `PATCH` | `/api/admin/secciones/{id}` | ADMIN / OPERADOR | Actualizar sección, visibilidad (`publicado`) o mover orden |
| **Capacitación** | `DELETE`| `/api/admin/secciones/{id}` | ADMIN / OPERADOR | Eliminar sección y contenidos en cascada |
| **Capacitación** | `POST` | `/api/admin/contenidos` | ADMIN / OPERADOR | Crear nuevo contenido/material en sección |
| **Capacitación** | `PATCH` | `/api/admin/contenidos/{id}` | ADMIN / OPERADOR | Actualizar contenido, visibilidad o mover orden |
| **Capacitación** | `DELETE`| `/api/admin/contenidos/{id}` | ADMIN / OPERADOR | Eliminar contenido específico |
| **Recetas Admin**| `GET` | `/api/admin/recetas` | ADMIN / OPERADOR | Listado de recetas administrativas |
| **Recetas Admin**| `POST`| `/api/admin/recetas` | ADMIN / OPERADOR | Crear receta con atributos completos y productos relacionados |
| **Recetas Admin**| `PUT` | `/api/admin/recetas/{id}` | ADMIN / OPERADOR | Actualizar receta existente |
| **Recetas Admin**| `DELETE`| `/api/admin/recetas/{id}` | ADMIN / OPERADOR | Eliminación lógica/física de receta |
| **Recetas Admin**| `POST`| `/api/admin/recetas/batch` | ADMIN / OPERADOR | Sincronización transaccional masiva de recetas y configuración |
| **Turismo Admin**| `GET` | `/api/admin/turismo` | ADMIN / OPERADOR | Listado de atractivos turísticos con datos editoriales |
| **Turismo Admin**| `POST`| `/api/admin/turismo` | ADMIN / OPERADOR | Crear atractivo con duración, dificultad, imagen y video |
| **Turismo Admin**| `PUT` | `/api/admin/turismo/{id}` | ADMIN / OPERADOR | Actualizar atractivo turístico |
| **Turismo Admin**| `DELETE`| `/api/admin/turismo/{id}`| ADMIN / OPERADOR | Eliminar atractivo turístico |
| **Contacto** | `POST` | `/api/contacto`, `/api/contact` | Público | Envío con validación de consentimiento y filtro antispam honeypot |

---

## 4. Pruebas y Aceptación

### 4.1 Resumen de Cobertura de Pruebas
Se ejecutó la suite completa de pruebas unitarias y de integración sobre PostgreSQL:
- `PageContentIntegrationTest`:
  - Retorno de contenido por defecto para slugs sin registro previo.
  - Persistencia y actualización de contenido editorial en PostgreSQL.
  - Validación de allowlist de slugs (rechazo `400 Bad Request` para slugs no permitidos).
  - Detección de conflicto de versión concurrente (retorno de `409 Conflict`).
- `TrainingContentIntegrationTest`:
  - Retorno exclusivo de secciones y materiales publicados en `/api/capacitacion`.
  - Mutación atómica de orden de secciones (`mover: "arriba"` / `"abajo"`).
  - Validación de campos según tipo de contenido (`texto` sin body = `400`, `video` sin url = `400`).
  - Eliminación en cascada de contenidos al borrar la sección padre.
- `AdminRecipeIntegrationTest`:
  - CRUD administrativo completo de recetas.
  - Sincronización transaccional por lote (`POST /api/admin/recetas/batch`) con actualización de página editorial y eliminación de recetas ausentes en el lote.
- `AdminTouristAttractionIntegrationTest`:
  - CRUD administrativo de turismo con campos de duración, dificultad, foto y video.
  - Alternancia de publicación (`PATCH /api/admin/turismo/{id}/publicacion`).
- `AssociationExtendedFieldsIntegrationTest`:
  - Persistencia de campos extendidos de ficha técnica (`parroquia`, `socios`, `altitud_msnm`, `horario_atencion`, `registro_bpm`, `campos_ficha`).
  - Preservación íntegra de campos extendidos tras actualización de slug.
- `ContactValidationIntegrationTest`:
  - Validación obligatoria de aceptación de políticas de privacidad (`privacyAccepted = true`).
  - Validación de formato y longitud de correo electrónico (RFC 5321).
  - Trampa honeypot (`hp_field`): respuesta 201 exitosa sin inserción en base de datos.
- `MigrationIntegrityTest`:
  - Validación de script de migración `20261115_cms_and_editorial_content.sql` y `06_cms_and_editorial_content.sql`.

### 4.2 Integración con Frontend
- El frontend Next.js (`http://localhost:3000`) se verificó de manera no intrusiva y sin realizar modificaciones sobre sus archivos de código fuente.
- Las vistas públicas de Home, Tienda, Recetas, Turismo, Asociaciones y Contacto se comunican fluidamente con las APIs de Spring Boot sin discrepancias en los esquemas de respuesta.
