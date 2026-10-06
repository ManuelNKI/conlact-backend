# BE-25 - POST `/api/contacto` y `contact_messages`

Documentación técnica de la implementación del módulo de mensajería y formulario institucional de contacto, basada en el catálogo de integración `CONTRATOS_BACKEND_BE14_A_BE41.md` y en el modelo relacional de base de datos de CONLAC-T.

---

## 1. Convenciones y Seguridad

- **URL Base:** `http://localhost:8080` (Backend Spring Boot) / `http://localhost:3000` (Next.js Frontend).
- **Rutas con Soporte Dual (Español / Inglés):**
  - Formulario público: `/api/contacto` y `/api/contact`.
  - Panel administrativo: `/api/admin/contacto`, `/api/admin/contact`, `/api/admin/mensajes-contacto`, `/api/admin/contact-messages`.
- **Autenticación y Seguridad:**
  - El envío de mensajes desde el formulario público es completamente abierto (`permitAll()`), sin necesidad de token de autenticación.
  - La lectura y resolución de mensajes en `/api/admin/contacto/**` exige cabecera `Authorization: Bearer <supabase_jwt_token>` con rol verificado `admin` y perfil activo (`is_active = true`) en la tabla `profiles`.
- **Persistencia:** Tabla `public.contact_messages` en PostgreSQL (Supabase).
- **Notificación:** Despacho automático y asíncrono (`@Async`) de alertas de correo electrónico al administrador del consorcio (`EmailNotificationService`).

---

## 2. Catálogo de Endpoints (BE-25)

| Método | Ruta en Español | Alias en Inglés | Seguridad | Código Éxito | Descripción |
|---|---|---|---|---|---|
| **POST** | `/api/contacto` | `/api/contact` | Pública (`permitAll`) | `201 Created` | Procesa y persiste un mensaje desde el formulario de contacto institucional |
| **GET** | `/api/admin/contacto` | `/api/admin/contact` | Admin (`ROLE_ADMIN`) | `200 OK` | Listado histórico de mensajes de contacto recibidos |
| **PATCH** | `/api/admin/contacto/{id}/resolver` | `/api/admin/contact/{id}/resolve` | Admin (`ROLE_ADMIN`) | `200 OK` | Marca un mensaje de contacto como atendido/resuelto |
| **PATCH** | `/api/admin/contacto/{id}/reabrir` | `/api/admin/contact/{id}/reopen` | Admin (`ROLE_ADMIN`) | `200 OK` | Reabre un mensaje para seguimiento |

---

## 3. Especificación de Cargas Útiles (Payloads)

### A. Envío de Formulario de Contacto (`POST /api/contacto` o `/api/contact`)
- **Headers:** `Content-Type: application/json`
- **Request Body:**
```json
{
  "nombre": "María Morales",
  "email": "maria.morales@empresa.ec",
  "telefono": "+593984561234",
  "asunto": "Pedido institucional al por mayor",
  "mensaje": "Deseamos cotizar 200 unidades de queso fresco semanalmente para nuestra cadena de cafeterías en Ambato.",
  "privacy_accepted": true
}
```

- **Validaciones:**
  - `nombre` *(obligatorio, máx 150 caracteres)*: Nombre o razón social del remitente.
  - `email` *(obligatorio, formato de correo válido, máx 255 caracteres)*.
  - `telefono` *(opcional, máx 50 caracteres)*.
  - `asunto` *(obligatorio, máx 200 caracteres)*.
  - `mensaje` *(obligatorio, máx 5000 caracteres)*.
  - `privacy_accepted` *(opcional, booleano)*: Aceptación de política de privacidad.

- **Response Body (`201 Created`):**
```json
{
  "id": "e2a149b8-1549-411a-8289-4b68dcb80782",
  "status": "received",
  "message": "Mensaje recibido correctamente. Un asesor del consorcio se comunicará a la brevedad."
}
```

---

### B. Listado Administrativo (`GET /api/admin/contacto`)
- **Query Params opcionales:** `?pendientes=true` (filtra únicamente mensajes no resueltos).
- **Response Body (`200 OK`):**
```json
[
  {
    "id": "e2a149b8-1549-411a-8289-4b68dcb80782",
    "nombre": "María Morales",
    "email": "maria.morales@empresa.ec",
    "telefono": "+593984561234",
    "asunto": "Pedido institucional al por mayor",
    "mensaje": "Deseamos cotizar 200 unidades de queso fresco...",
    "privacy_accepted": true,
    "is_resolved": false,
    "created_at": "2026-10-04T18:40:00Z"
  }
]
```

---

### C. Marcar Mensaje como Resuelto (`PATCH /api/admin/contacto/{id}/resolver`)
- **Response Body (`200 OK`):** Devuelve el objeto del mensaje actualizado con `is_resolved: true`.

---

## 4. Disparo Asíncrono de Correos (`EmailNotificationService`)

Al persistirse el mensaje, se dispara el método asíncrono `@Async`:
```java
emailNotificationService.sendContactNotificationToAdmin(savedMessage);
```
- Notifica al buzón configurado del administrador (`app.mail.admin-recipient`).
- El proceso se ejecuta en un hilo secundario para garantizar tiempos de respuesta ultrarrápidos (< 50ms) en la API del cliente.
- Las posibles indisponibilidades temporales del servidor SMTP no interrumpen ni hacen fallar la confirmación al cliente (`try-catch` aislado con registro estructurado en logs).

---

## 5. Pruebas Automatizadas

1. **Pruebas Unitarias de Servicio (`ContactServiceTest`):**
   - Sanitización de espacios y minúsculas en correos.
   - Disparo de evento al servicio de correo.
   - Filtros de mensajes pendientes y actualización de estado.
2. **Pruebas de Controlador (`ContactControllerTest`):**
   - Validación de Bean Validation (nombres en blanco, emails malformados, mensajes vacíos devuelven 400).
   - Rutas en español e inglés retornan 201 Created.
3. **Pruebas de Integración con Testcontainers (`ContactIntegrationTest`):**
   - Persistencia real en base de datos PostgreSQL.
   - Protección de endpoints administrativos (401 sin token, 200 con token admin).
   - Verificación de resolución de mensajes en base de datos.
