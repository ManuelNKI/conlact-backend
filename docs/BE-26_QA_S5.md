# [BE-26] Pruebas SMTP, Turismo y QA S5

## 1. Ficha Técnica del Milestone
- **Milestone**: Semana 5 (S5) - Recetas Tradicionales, Rutas de Turismo Comunitario y Servicio de Mensajería de Contacto.
- **Tickets Cubiertos**:
  - `[BE-22]`: API de Recetas Tradicionales con Vinculación N:M a Productos (`recipe_products`, recomendación de quesos autóctonos).
  - `[BE-23]`: API de Turismo Comunitario (`tourist_attractions`, geolocalización lat/lng, filtrado por asociación).
  - `[BE-24]`: Servicio SMTP de Correos con JavaMail Mockeado (`@MockitoBean EmailNotificationService`).
  - `[BE-25]`: `POST /api/contacto` y persistencia en `contact_messages` con ciclo de moderación administrativa.
  - `[BE-26]`: Pruebas SMTP, Turismo y QA S5.
- **Entorno de Ejecución**: OpenJDK 25.0.4.1, Spring Boot 3.4.3, PostgreSQL 16 (Testcontainers), Spring MockMvc, Mockito Bean Overrides.
- **Resultado Global**: **100% PASS** (Cero tráfico SMTP saliente accidental, Integridad relacional N:M certificada en base de datos real).

---

## 2. Arquitectura del Módulo y Estrategia de Mockeo SMTP

```mermaid
graph LR
    User[Usuario / Turista] -->|POST /api/contacto| ContactCtrl[ContactController]
    User -->|GET /api/recetas| RecipeCtrl[RecipeController]
    User -->|GET /api/turismo| TourismCtrl[TouristAttractionController]
    
    ContactCtrl --> ContactSvc[ContactService]
    RecipeCtrl --> RecipeSvc[RecipeService]
    TourismCtrl --> TourismSvc[TouristAttractionService]
    
    ContactSvc --> DB[(PostgreSQL 16: contact_messages)]
    RecipeSvc --> DB_NM[(PostgreSQL 16: recipes & recipe_products)]
    TourismSvc --> DB_T[(PostgreSQL 16: tourist_attractions)]
    
    ContactSvc -.->|Trigger Notificación| MockSmtp[EmailNotificationService Mockeado @MockitoBean]
    MockSmtp -.x|BLOQUEADO EN TESTS| RealSmtp[Servidor SMTP Externo JavaMail]
```

### Directrices de Calidad del Milestone:
1. **Aislamiento Total del Canal de Red SMTP (`Zero External Network Calls`)**:
   - En entornos de integración continua (CI) y pruebas automáticas, enviar correos reales ocasiona bloqueos de IP, consumo innecesario de cuotas SMTP y fallos por latencia de red.
   - Se utiliza `@MockitoBean EmailNotificationService` para interceptar la llamada, capturar el mensaje exacto generado por el servicio y certificar que contiene los datos correctos del remitente y destinatario sin emitir ningún paquete de red hacia el exterior.
2. **Validación de la Relación Muchos a Muchos (N:M)**:
   - Para `[BE-22]`, no solo se evalúa la consulta de recetas, sino la tabla intermedia `recipe_products`, certificando que cada receta incluye los quesos recomendados para su elaboración con sus respectivos metadatos de disponibilidad.
3. **Validación Rigurosa de Datos de Contacto (`[BE-25]`)**:
   - Comprobación de que direcciones de correo mal formadas, textos vacíos o intentos de evasión de políticas de privacidad son interceptados con código `400 BAD REQUEST`.

---

## 3. Desglose Exhaustivo de Casos de Prueba Automatizados

### Suite A: Servicio de Contacto y Mockeo de Notificaciones (`ContactSmtpMockIntegrationTest`)

#### TC-S5-001: Envío de Mensaje de Contacto y Notificación Mockeada
- **Método**: `shouldStoreContactMessageAndTriggerMockedEmailNotification()`
- **Ticket**: `[BE-24]`, `[BE-25]`, `[BE-26]`
- **Objetivo**: Comprobar que `POST /api/contacto` persiste correctamente la solicitud del usuario en la tabla `contact_messages` y dispara el método de notificación al administrador sin enviar tráfico SMTP real.
- **Precondiciones**:
  - Mock de `EmailNotificationService` intercepta `sendContactNotificationToAdmin(any())` retornando `void`.
- **Payload de Entrada**:
  ```json
  {
    "nombre": "Carlos Mendoza",
    "email": "carlos.mendoza@agroexport.com",
    "telefono": "+593998877665",
    "asunto": "Consulta de exportación de quesos",
    "mensaje": "Deseamos contactar a las queserías de Chimborazo para alianza de distribución.",
    "politica_privacidad": true
  }
  ```
- **Aserciones Verificadas**:
  1. **Respuesta HTTP**:
     - Status: `201 CREATED`.
     - JSON contiene `id` autogenerado (UUID) y `status == "received"`.
  2. **Persistencia en PostgreSQL**:
     - `contactMessageRepository.findById(createdId)` está presente.
     - `saved.getName() == "Carlos Mendoza"`.
     - `saved.getEmail() == "carlos.mendoza@agroexport.com"`.
     - `saved.getIsResolved() == false`.
  3. **Verificación Mockito SMTP**:
     ```java
     verify(emailNotificationService, times(1)).sendContactNotificationToAdmin(argThat(msg ->
         msg.getId().equals(createdId) && msg.getEmail().equals("carlos.mendoza@agroexport.com")
     ));
     ```
     Certifica que el servicio fue invocado con el objeto exacto recién creado.
- **Resultado**: **PASS**.

---

#### TC-S5-002: Alias en Inglés para el Formulario de Contacto
- **Método**: `shouldAcceptContactViaEnglishAlias()`
- **Ticket**: `[BE-25]`
- **Entrada**: Petición `POST /api/contact` con datos válidos en inglés.
- **Aserciones Verificadas**:
  - Status `201 CREATED`.
  - El registro se inserta exitosamente en la base de datos PostgreSQL.
- **Resultado**: **PASS**.

---

#### TC-S5-003: Validación Exhaustiva de Payloads Mal Formados (Rechazo 400 Bad Request)
- **Método**: `shouldRejectMalformedContactPayloads()`
- **Ticket**: `[BE-25]`, `[BE-26]`
- **Objetivo**: Garantizar que el backend rechaza de forma determinista cualquier mensaje que viole las restricciones de integridad y sanidad de datos.
- **Casos de Borde Probados**:
  1. **Email Inválido**: `"email": "no-es-un-correo-valido"` -> Código HTTP 400.
  2. **Nombre en Blanco**: `"nombre": "   "` -> Código HTTP 400.
  3. **Asunto en Blanco**: `"asunto": "  "` -> Código HTTP 400.
  4. **Cuerpo del Mensaje Vacío**: `"mensaje": "   "` -> Código HTTP 400.
- **Resultado**: **PASS** (4/4 subcasos rechazados correctamente).

---

#### TC-S5-004: Flujo Administrativo de Gestión y Resolución de Mensajes
- **Método**: `shouldAllowAdminToManageAndResolveMessages()`
- **Ticket**: `[BE-25]`
- **Flujo Paso a Paso**:
  1. Inserción directa de un mensaje no resuelto (`is_resolved = false`).
  2. `GET /api/admin/contacto` con token de administrador -> Valida HTTP 200 y presencia del mensaje en el listado de moderación.
  3. `PATCH /api/admin/contacto/{id}/resolver` con token de admin -> Valida HTTP 200 y respuesta `is_resolved: true`.
  4. Consulta en base de datos:
     - `contactMessageRepository.findById(msg.getId()).get().getIsResolved() === true`.
- **Resultado**: **PASS**.

---

### Suite B: Recetas Tradicionales y Asociación N:M (`RecipeIntegrationTest`)

#### TC-S5-005: Catálogo de Recetas en Rutas Duales
- **Método**: `shouldReturnPublishedRecipesFromDatabase()`
- **Ticket**: `[BE-22]`, `[BE-26]`
- **Rutas Probadas**: `GET /api/recetas` y `GET /api/recipes`.
- **Aserciones Verificadas**:
  - Código HTTP 200 OK.
  - Arreglo con al menos 2 recetas tradicionales sembradas.
  - Atributos obligatorios: `id`, `titulo`, `slug`, `ingredientes`, `pasos`.
- **Resultado**: **PASS**.

#### TC-S5-006: Detalle de Receta por Slug Sembrado
- **Método**: `shouldFindRecipeBySlug()`
- **Ticket**: `[BE-22]`
- **Entrada**: `GET /api/recetas/locro-de-papa-con-queso-fresco-de-altura`.
- **Aserciones Verificadas**:
  - Status `200 OK`.
  - `slug === "locro-de-papa-con-queso-fresco-de-altura"`.
  - `titulo` contiene `"Locro Tradicional"`.
  - `porciones === 4`.
- **Resultado**: **PASS**.

#### TC-S5-007: Manejo de Receta Inexistente (404 Not Found)
- **Método**: `shouldReturn404ForUnknownRecipe()`
- **Ticket**: `[BE-22]`
- **Entrada**: `GET /api/recetas/receta-inexistente-123`.
- **Aserciones Verificadas**:
  - Status `404 NOT FOUND`.
- **Resultado**: **PASS**.

#### TC-S5-008: Certificación de Relación N:M en Tabla `recipe_products` [BE-22]
- **Método**: `shouldVerifyRecipeProductsRelation()`
- **Ticket**: `[BE-22]`, `[BE-26]`
- **Objetivo**: Validar que la receta está enlazada en la base de datos relacional con al menos un queso artesanal recomendado.
- **Verificación SQL Directa vía JdbcTemplate**:
  ```sql
  -- Verificar vinculación N:M
  SELECT COUNT(*) FROM public.recipe_products WHERE recipe_id = ?::uuid;
  -- Resultado: >= 1 vinculación confirmada

  -- Verificar bandera de recomendación destacada
  SELECT is_recommended FROM public.recipe_products WHERE recipe_id = ?::uuid LIMIT 1;
  -- Resultado: true
  ```
- **Resultado**: **PASS** (Integridad relacional N:M certificada).

---

### Suite C: Rutas Turísticas y Geolocalización (`TouristAttractionIntegrationTest`)

#### TC-S5-009: Consulta de Rutas y Atractivos Turísticos
- **Método**: `shouldReturnTouristAttractionsWithDualRoutes()`
- **Ticket**: `[BE-23]`
- **Rutas Probadas**: `GET /api/turismo` y `GET /api/tourism`.
- **Aserciones Verificadas**:
  - Status `200 OK`.
  - Lista de atractivos con campos: `id`, `nombre`, `slug`, `latitud`, `longitud`.
- **Resultado**: **PASS**.

#### TC-S5-010: Filtrado de Atractivos Turísticos por Asociación
- **Método**: `shouldFindAttractionBySlugAndFilterByAssociation()`
- **Ticket**: `[BE-23]`
- **Casos Probados**:
  1. `GET /api/turismo/volcan-chimborazo-refugio`: Retorna detalle con coordenadas geográficas precisas.
  2. `GET /api/turismo?asociacion_id={{association_id}}`: Retorna únicamente atractivos situados en la zona de influencia de la asociación productora.
- **Resultado**: **PASS**.

---

## 4. Desglose Técnico de la Colección Postman Versionada

- **Ruta del Archivo**: [`conlact-backend/docs/postman/BE26_S5_recipes_tourism_contact_smtp.postman_collection.json`](file:///d:/2Proyectos/CONLACT/conlact-backend/docs/postman/BE26_S5_recipes_tourism_contact_smtp.postman_collection.json)
- **Formato**: Postman Collection Schema v2.1.0.

### Variables de Entorno de la Colección:
| Variable | Valor por Defecto | Propósito |
|---|---|---|
| `base_url` | `http://localhost:8080` | URL del servidor |
| `admin_token` | *(Bearer JWT)* | Token para rutas administrativas `/api/admin/contacto` |
| `recipe_slug` | `locro-de-papa-con-queso-fresco-de-altura` | Slug de receta sembrada |
| `tourism_slug` | `volcan-chimborazo-refugio` | Slug de atractivo turístico |

### Detalle de Requests y Scripts de Aserción:

#### 1. Enviar Formulario de Contacto
- **Método**: `POST {{base_url}}/api/contacto`
- **Body**:
  ```json
  {
    "nombre": "Comprador Mayorista Postman",
    "email": "mayorista@postman-test.com",
    "telefono": "0998877665",
    "asunto": "Pedido de prueba automatizado",
    "mensaje": "Mensaje de prueba para verificar persistencia y notificación mockeada.",
    "politica_privacidad": true
  }
  ```
- **Tests Script (JavaScript)**:
  ```javascript
  pm.test("Status 201 y mensaje creado con ID persistido", function () {
      pm.response.to.have.status(201);
      var json = pm.response.json();
      pm.expect(json).to.have.property("id");
      pm.variables.set("new_contact_id", json.id);
  });
  ```

#### 2. Consultar Recetas Tradicionales con Productos Vinculados
- **Método**: `GET {{base_url}}/api/recetas/{{recipe_slug}}`
- **Tests Script**:
  ```javascript
  pm.test("Receta encontrada con detalle y productos recomendados", function () {
      pm.response.to.have.status(200);
      var json = pm.response.json();
      pm.expect(json.slug).to.eql(pm.variables.get("recipe_slug"));
      pm.expect(json).to.have.property("productos_recomendados");
  });
  ```

#### 3. Moderación y Resolución Administrativa de Mensajes de Contacto
- **Método**: `PATCH {{base_url}}/api/admin/contacto/{{new_contact_id}}/resolver`
- **Headers**: `Authorization: Bearer {{admin_token}}`
- **Tests Script**:
  ```javascript
  pm.test("Mensaje resuelto exitosamente con status 200", function () {
      pm.response.to.have.status(200);
      var json = pm.response.json();
      pm.expect(json.is_resolved).to.be.true;
  });
  ```

---

## 5. Instrucciones de Reproducción y Comandos de Consola

### Ejecución de Pruebas de Recetas, Turismo y Mock SMTP con Maven Wrapper
```powershell
# Configurar JDK 25
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.4.1'

# Ejecutar la suite completa de S5
.\mvnw.cmd test -Dtest=ContactSmtpMockIntegrationTest,RecipeIntegrationTest,TouristAttractionIntegrationTest
```

### Ejecución de la Colección Postman mediante Newman CLI
```powershell
npx -y newman run docs/postman/BE26_S5_recipes_tourism_contact_smtp.postman_collection.json `
  --env-var "base_url=http://localhost:8080" `
  --env-var "admin_token=YOUR_JWT_HERE" `
  --reporters cli,json --reporter-json-export target/newman-s5-report.json
```
