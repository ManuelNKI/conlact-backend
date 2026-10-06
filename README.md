# CONLAC-T - Backend API 🧀🥛

Backend para la plataforma de comercio electrónico y gestión comunitaria de la **Asociación de Productores de Lácteos de Tungurahua (CONLAC-T)**.

Proyecto desarrollado en la **Universidad Técnica de Ambato (UTA)** — Facultad de Ingeniería en Sistemas, Electrónica e Industrial (FISEI) — Carrera de Software.

---

## 🚀 Tecnologías Principales

- **Lenguaje:** Java 25 (versión de compilación definida en `pom.xml`).
- **Framework:** Spring Boot 4.1.1.
- **Persistencia:** Spring Data JPA / Hibernate
- **Base de Datos:** PostgreSQL 16
- **Seguridad:** Spring Security (Tokens JWT bajo estándar RFC 7519, validación asimétrica ECC NIST P-256 / ES256 y JWKS)
- **Almacenamiento Cloud:** Supabase Storage (Contrato desacoplado `IStorage`)
- **Contenerización y Testing:** Docker Compose, Testcontainers
- **Gestión de Construcción:** Apache Maven (Wrapper incluido)

---

## 📋 Requisitos Previos

Asegúrate de contar con lo siguiente en tu equipo:

1. **Java Development Kit (JDK):** Versión 25 instalada. Java 21 no compila el proyecto con su configuración actual.
2. **Git:** Para control de versiones.
3. **Docker & Docker Compose:** *(Opcional pero recomendado para levantar la base de datos local)*.

---

## 🛠️ Guía de Puesta en Marcha (Entorno Local)

### 1. Clonar el repositorio
```bash
git clone <URL_DEL_REPOSITORIO>
cd conlact-backend/conlact-backend
```

### 2. Configurar las Variables de Entorno
Copia la plantilla de variables de entorno [.env.example](.env.example) a un nuevo archivo `.env`:

```bash
# En Windows PowerShell o CMD:
copy .env.example .env

# En Linux o macOS:
cp .env.example .env
```

Edita el archivo recién creado `.env` con las credenciales de tu proyecto de Supabase:

```properties
# ------------------------------------------------------------------------------
# Configuración Supabase
# ------------------------------------------------------------------------------
SUPABASE_URL=https://tu-proyecto.supabase.co
SUPABASE_SERVICE_ROLE_KEY=sb_secret_tu_clave_secreta
SUPABASE_JWT_PUBLIC_KEY=f037aa7c-1631-49f4-9f86-78cee67948e2
SUPABASE_JWT_ISSUER=https://tu-proyecto.supabase.co/auth/v1

# ------------------------------------------------------------------------------
# Buckets de Storage
# ------------------------------------------------------------------------------
STORAGE_PRODUCT_BUCKET=product-images
STORAGE_RECIPE_BUCKET=recipe-images
STORAGE_PAYMENT_BUCKET=payment-proofs
```

> [!NOTE]
> El archivo `.env` se encuentra excluido en [.gitignore](.gitignore), por lo que tus credenciales permanecerán seguras y nunca se subirán a GitHub.

---

### 3. Iniciar la Base de Datos Local con Docker Compose

Ejecuta el siguiente comando para iniciar el contenedor de PostgreSQL con las tablas maestras y semillas iniciales precargadas:

```bash
docker compose up -d
```

- **Host:** `localhost`
- **Puerto:** `5435`
- **Base de datos:** `conlact_local`
- **Usuario / Contraseña:** `postgres` / `postgres`
- **Scripts de inicialización automática:**
  - [`docker/init/00_auth_compat.sql`](docker/init/00_auth_compat.sql): Rol y función de Auth necesarios para las políticas del PostgreSQL local.
  - [`docker/init/01_CONLACT_Modelo_Base.sql`](docker/init/01_CONLACT_Modelo_Base.sql): Esquema relacional base y RLS.
  - [`docker/init/02_seed.sql`](docker/init/02_seed.sql): Datos maestros de prueba y asociaciones.
  - [`docker/init/03_storage_buckets.sql`](docker/init/03_storage_buckets.sql): Configuración de buckets y storage.
  - [`docker/init/04_association_images.sql`](docker/init/04_association_images.sql): Gestión de URLs de fotos de asociaciones [BE-14].
  - [`docker/init/05_extend_testimonials.sql`](docker/init/05_extend_testimonials.sql): Avatar, calificación y moderación de testimonios.

Si una base ya creada falla con `Schema validation: missing table [association_images]`, ejecutar desde la raíz del proyecto:

```powershell
.\docker\repair-local-db.ps1
```

El script guarda un respaldo en `target/local-db-backups`, completa los scripts pendientes y conserva el volumen y las semillas existentes. Docker solo ejecuta la inicialización automática cuando el volumen está vacío; reiniciar un contenedor con una base parcial no completa el esquema.

Docker Compose levanta únicamente PostgreSQL. Spring usa esa base en el perfil `local`; Supabase Auth y Storage se configuran aparte mediante sus credenciales. Las tablas locales de Storage no equivalen a tener un servidor Supabase funcionando en Docker.

---

### 4. Compilar y Ejecutar el Backend

#### En Windows (PowerShell):
```powershell
.\start-local.ps1
```

El script selecciona un JDK 25 registrado en `JAVA_HOME` o disponible en PATH y activa el perfil `local`. Utiliza `target/java-sockets` como directorio temporal de sockets de Java para evitar el error `Unable to establish loopback connection` observado en Windows. Restaura las variables de la sesión al finalizar. La propiedad `jdk.net.unixdomain.tmpdir` está documentada por [Oracle para Java 25](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/net/doc-files/net-properties.html).

También se puede usar `.\mvnw.cmd spring-boot:run` desde una sesión con JDK 25 seleccionado y sin ese problema del directorio temporal.

#### En Linux / macOS:
```bash
./mvnw spring-boot:run
```

El servidor iniciará en el puerto **`8080`**:
- URL Base de la API: `http://localhost:8080`

---

## 🧪 Ejecución de Pruebas Automatizadas

Requiere **JDK 25** y **Docker Desktop** activo (Testcontainers levanta PostgreSQL automáticamente).

### Suite Completa (todos los tests)
```powershell
# Windows
.\mvnw.cmd test

# Linux / macOS
./mvnw test
```

### Pruebas Unitarias y de Infraestructura Específicas
Para ejecutar pruebas individuales de seguridad, storage o integración en vivo:
```powershell
# Pruebas unitarias de tokens JWT (claves ECC P-256 y HMAC fallback)
./mvnw test "-Dtest=JwtTokenProviderTest"

# Pruebas unitarias de Supabase Storage con mocks (upload, download, signed URLs, delete)
./mvnw test "-Dtest=SupabaseStorageServiceTest"

# Pruebas de integración en vivo contra Supabase (requiere SUPABASE_URL y SERVICE_ROLE_KEY en .env)
./mvnw test "-Dtest=SupabaseLiveConfigurationTest"
```

### Por Milestone Específico (QA)

| Ticket | Área | Comando |
|--------|------|---------|
| `[BE-16]` | Asociaciones, Testimonios y Fotos | `.\mvnw.cmd test "-Dtest=TestimonialControllerTest,AdminTestimonialControllerTest,TestimonialIntegrationTest,AssociationIntegrationTest"` |
| `[BE-21]` | Estrés de Inventario y Catálogo | `.\mvnw.cmd test "-Dtest=InventoryConcurrencyStressIntegrationTest,CatalogComprehensiveIntegrationTest"` |
| `[BE-26]` | Recetas, Turismo y Contacto (SMTP Mock) | `.\mvnw.cmd test "-Dtest=RecipeIntegrationTest,TouristAttractionIntegrationTest,ContactSmtpMockIntegrationTest"` |
| `[BE-31]` | Flujo Transaccional, Webhooks y Auditoría Financiera | `.\mvnw.cmd test "-Dtest=FinancialAuditOrderTest,OrderTransactionalFlowIntegrationTest"` |

### Colecciones Postman con Newman CLI
Las colecciones están en `docs/postman/`. Ejecutar con el servidor levantado en `:8080`:
```bash
npx newman run docs/postman/BE16_S3_associations_testimonials.postman_collection.json --env-var "base_url=http://localhost:8080"
npx newman run docs/postman/BE21_S4_inventory_stress_catalog.postman_collection.json       --env-var "base_url=http://localhost:8080"
npx newman run docs/postman/BE26_S5_recipes_tourism_contact_smtp.postman_collection.json   --env-var "base_url=http://localhost:8080"
npx newman run docs/postman/BE31_S6_transactional_financial_checkout.postman_collection.json --env-var "base_url=http://localhost:8080"
```

---

## 📂 Arquitectura y Estructura del Proyecto

El código está estructurado siguiendo principios de **Arquitectura Hexagonal (Puertos y Adaptadores)** y **Clean Architecture**:

```text
src/main/java/com/conlact/conlact_backend/
├── config/          # Configuraciones de Spring, CORS, carga de .env y Beans
├── controller/      # Controladores REST API (Endpoints públicos y administrativos)
├── dto/             # Data Transfer Objects (Request y Response)
├── entity/          # Entidades JPA y Enums de PostgreSQL
├── exception/       # Manejador global de excepciones (GlobalExceptionHandler)
├── repository/      # Interfaces Spring Data JPA
├── security/        # Filtro de autenticación JWT y configuración de Spring Security
├── service/         # Lógica de negocio y casos de uso
└── storage/         # Contrato desacoplado IStorage e implementación de Supabase Storage
```

---

## 🔒 Seguridad y Autenticación

- **Rutas Públicas:** 
  - `GET /api/associations/**`
  - `GET /api/categories/**`
  - `GET /api/products/**`
  - `GET /api/recipes/**`
  - `GET /api/testimonials/**`
- **Rutas Protegidas (ROLE_ADMIN):**
  - Todas las rutas bajo `/api/admin/**` requieren un token Bearer en el encabezado `Authorization`:
    ```text
    Authorization: Bearer <SUPABASE_JWT_TOKEN>
    ```
- **Políticas CORS:** Habilitadas por defecto para `http://localhost:3000` (Next.js) y `http://localhost:5173` (Vite / React).

---

## 📦 Almacenamiento en la Nube (Buckets de Supabase)

El backend interactúa con 3 buckets independientes:
1. **`product-images`** (Público): Fotografías de quesos y derivados lácteos.
2. **`recipe-images`** (Público): Fotografías de platos típicos y recetas comunitarias.
3. **`payment-proofs`** (Privado): Comprobantes de transferencias bancarias protegidos con URLs firmadas temporales.

## API de Testimonios y Administración de Productos (BE-15 / BE-19)

Los testimonios públicos están disponibles en `/api/testimonios` y `/api/testimonials`.
El CRUD de testimonios, productos, variantes y las operaciones transaccionales de stock requieren un perfil ADMIN activo.

Los contratos, reglas de publicación, ejemplos JSON, endpoints, pruebas y pendientes de arranque están documentados en [docs/BE15_BE19.md](docs/BE15_BE19.md).
La colección para revisión manual está en [docs/postman/BE15_BE19.postman_collection.json](docs/postman/BE15_BE19.postman_collection.json).

La API de testimonios también incluye aprobación, archivo, destacados opcionales, avatar y calificación de 1 a 5. Después de actualizar código sobre una base local existente, ejecutar `.\docker\repair-local-db.ps1` para aplicar la ampliación con respaldo.

## Correo SMTP asíncrono (BE-24)

`EmailNotificationService` usa JavaMailSender y plantillas HTML Thymeleaf para contacto y pedidos. El envío corre mediante `@Async` y devuelve un `CompletableFuture` que permite observar fallos. Por defecto está deshabilitado hasta configurar SMTP. Ver [uso, plantillas y configuración](docs/BE24_CORREO.md).

El formulario de contacto ya invoca el servicio después de guardar el mensaje. Sus notificaciones se dirigen a `MAIL_ADMIN_RECIPIENT`, o a `MAIL_FROM` cuando no se configura un destinatario aparte.
