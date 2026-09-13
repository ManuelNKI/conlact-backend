# CONLAC-T - Backend API 🧀🥛

Backend para la plataforma de comercio electrónico y gestión comunitaria de la **Asociación de Productores de Lácteos de Tungurahua (CONLAC-T)**.

Proyecto desarrollado en la **Universidad Técnica de Ambato (UTA)** — Facultad de Ingeniería en Sistemas, Electrónica e Industrial (FISEI) — Carrera de Software.

---

## 🚀 Tecnologías Principales

- **Lenguaje:** Java 25 / 21 LTS
- **Framework:** Spring Boot 4.x / Spring Framework 6.x
- **Persistencia:** Spring Data JPA / Hibernate
- **Base de Datos:** PostgreSQL 16
- **Seguridad:** Spring Security (Tokens JWT bajo estándar RFC 7519, validación asimétrica ECC NIST P-256 / ES256 y JWKS)
- **Almacenamiento Cloud:** Supabase Storage (Contrato desacoplado `IStorage`)
- **Contenerización y Testing:** Docker Compose, Testcontainers
- **Gestión de Construcción:** Apache Maven (Wrapper incluido)

---

## 📋 Requisitos Previos

Asegúrate de contar con lo siguiente en tu equipo:

1. **Java Development Kit (JDK):** Versión 21 o 25 instalada.
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
- **Scripts de inicialización automática:** [`docker/init/01_CONLACT_Modelo_Base.sql`](docker/init/01_CONLACT_Modelo_Base.sql) y [`02_seed.sql`](docker/init/02_seed.sql).

---

### 4. Compilar y Ejecutar el Backend

#### En Windows (PowerShell):
```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.4.1'  # Ajusta a la ruta de tu JDK si es diferente
./mvnw spring-boot:run
```

#### En Linux / macOS:
```bash
./mvnw spring-boot:run
```

El servidor iniciará en el puerto **`8080`**:
- URL Base de la API: `http://localhost:8080`

---

## 🧪 Ejecución de Pruebas Automatizadas

El proyecto cuenta con suites de pruebas unitarias y de integración:

```bash
# Ejecutar todas las pruebas unitarias y de configuración
./mvnw test

# Ejecutar una prueba específica
./mvnw test "-Dtest=JwtTokenProviderTest"
./mvnw test "-Dtest=SupabaseStorageServiceTest"
./mvnw test "-Dtest=SupabaseLiveConfigurationTest"
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
