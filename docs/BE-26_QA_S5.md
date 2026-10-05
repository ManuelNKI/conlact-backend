# [BE-26] Pruebas SMTP, Turismo y QA S5

## 1. Resumen Ejecutivo
Para el Milestone de la Semana 5 (S5), se desarrolló la suite de pruebas unitarias y de integración para tres módulos esenciales del portal de CONLAC-T:
1. **Recetas Tradicionales**: Preparaciones culinarias autóctonas que utilizan los quesos del consorcio.
2. **Atractivos Turísticos y Senderos**: Rutas vivenciales en las faldas del Chimborazo y queserías comunitarias.
3. **Formulario de Contacto Institucional**: Recepción y persistencia de consultas con **servidor SMTP mockeado** para interceptar y garantizar la ausencia absoluta de envíos de correos reales a servidores externos durante la ejecución de las pruebas.

---

## 2. Alcance Técnico
- **Aislamiento SMTP**: Utilización de `@MockitoBean` sobre `EmailNotificationService` en pruebas de integración, y configuración controlada de `app.mail.enabled=false`, asegurando que `sendContactNotificationToAdmin` sea verificado vía `verify()` de Mockito sin generar tráfico SMTP de red saliente.
- **Validación de Entradas (Jakarta Validation)**: Pruebas negativas que aseguran el retorno de código `400 BAD REQUEST` cuando se envían correos mal formados (`no-es-correo`), nombres vacíos, mensajes en blanco o longitudes no permitidas.
- **Rutas Duales S5**: `/api/recetas` y `/api/recipes`, `/api/turismo` y `/api/tourism`, `/api/contacto` y `/api/contact`.

---

## 3. Matriz de Pruebas Automatizadas

| Clase de Prueba | Tipo | Casos Cubiertos | Resultado |
|---|---|---|---|
| [`RecipeIntegrationTest`](file:///d:/2Proyectos/CONLACT/conlact-backend/src/test/java/com/conlact/conlact_backend/RecipeIntegrationTest.java) | Integración (PostgreSQL) | Consulta de recetas en rutas duales (`/api/recetas` y `/api/recipes`), consulta por slug `locro-de-papa-con-queso-fresco-de-altura`, validación 404 para slugs inexistentes y **certificación de vinculación N:M de productos recomendados (`recipe_products` - BE-22)**. | **PASS** |
| [`TouristAttractionIntegrationTest`](file:///d:/2Proyectos/CONLACT/conlact-backend/src/test/java/com/conlact/conlact_backend/TouristAttractionIntegrationTest.java) | Integración (PostgreSQL) | Listado de atractivos en rutas duales (`/api/turismo` y `/api/tourism`), filtro por asociación `asociacion_id`, detalle por ID y validación 404. | **PASS** |
| [`ContactSmtpMockIntegrationTest`](file:///d:/2Proyectos/CONLACT/conlact-backend/src/test/java/com/conlact/conlact_backend/ContactSmtpMockIntegrationTest.java) | Integración (PostgreSQL + Mockito SMTP) | Envío exitoso en `/api/contacto` y alias `/api/contact` retornando `201 CREATED`. Persistencia en `contact_messages`. Verificación de captura de argumentos en `EmailNotificationService` con 0 llamadas SMTP reales. Validaciones de rechazo 400 por nombre, asunto, mensaje o correo inválido. Gestión y resolución en `/api/admin/contacto`. | **PASS** |
| [`RecipeControllerTest`](file:///d:/2Proyectos/CONLACT/conlact-backend/src/test/java/com/conlact/conlact_backend/controller/RecipeControllerTest.java) | Unitario (MockMvc) | Aislamiento del controlador de recetas, serialización y mapeo de DTOs. | **PASS** |
| [`TouristAttractionControllerTest`](file:///d:/2Proyectos/CONLACT/conlact-backend/src/test/java/com/conlact/conlact_backend/controller/TouristAttractionControllerTest.java) | Unitario (MockMvc) | Coordenadas geográficas (`lat`, `lng`), campo `requiere_confirmacion`, tipología. | **PASS** |
| [`ContactControllerTest`](file:///d:/2Proyectos/CONLACT/conlact-backend/src/test/java/com/conlact/conlact_backend/controller/ContactControllerTest.java) | Unitario (MockMvc) | Formulario de contacto y manejo de excepciones con `GlobalExceptionHandler`. | **PASS** |

---

## 4. Colección Postman Versionada
- **Archivo**: [`conlact-backend/docs/postman/BE26_S5_recipes_tourism_contact_smtp.postman_collection.json`](file:///d:/2Proyectos/CONLACT/conlact-backend/docs/postman/BE26_S5_recipes_tourism_contact_smtp.postman_collection.json)
- **Variables de Entorno**:
  - `{{base_url}}`: `http://localhost:8080`
  - `{{admin_token}}`: Token Bearer JWT de admin
  - `{{recipe_slug}}`: `locro-de-papa-con-queso-fresco-de-altura`
  - `{{attraction_id}}`: `00000000-0000-0000-0000-000000000001`
  - `{{association_id}}`: `a0000000-0000-0000-0000-000000000001`
  - `{{message_id}}`: Almacenado dinámicamente al enviar mensaje

### Pruebas Postman Automatizadas Incluidas
1. **Listar recetas [ES / EN]**: Valida código `200 OK` y presencia de arreglos de ingredientes y pasos.
2. **Detalle de receta por slug**: Valida código `200 OK`, título y porciones.
3. **Consulta receta inexistente**: Valida código `404 NOT FOUND`.
4. **Listar atractivos [ES / EN]**: Valida código `200 OK`, coordenadas `lat` y `lng`.
5. **Filtrar atractivos por asociación**: Valida filtrado en query param `?asociacion_id=`.
6. **Enviar contacto [ES / EN]**: Valida código `201 CREATED`, retorna `status: "received"` y UUID.
7. **Validación 400 Bad Request**: Valida rechazo automático ante correo o campos vacíos.
8. **Administración de contacto**: Consulta en `/api/admin/contacto` y resolución mediante PATCH `/{id}/resolver`.

---

## 5. Comando de Ejecución
```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.4.1'
.\mvnw.cmd test -Dtest=RecipeIntegrationTest,TouristAttractionIntegrationTest,ContactSmtpMockIntegrationTest,RecipeControllerTest,TouristAttractionControllerTest,ContactControllerTest
```
