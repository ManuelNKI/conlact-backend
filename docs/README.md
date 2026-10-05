# Documentación Maestra de Calidad (QA) y Pruebas Transaccionales - Backend CONLAC-T

Este repositorio contiene la arquitectura de aseguramiento de calidad (QA), el desglose exhaustivo de pruebas automáticas (unitarias, integración, estrés, concurrencia y financieras) y las colecciones oficiales de Postman v2.1.0 diseñadas para el **Backend de CONLAC-T** (`conlact-backend`).

---

## 📌 Control de Versiones y Entorno
- **Rama Feature de QA**: `feature/BE-16-21-26-31-qa-test-suites`
- **Ruta del Proyecto Backend**: `d:\2Proyectos\CONLACT\conlact-backend`
- **Ruta de Documentación Técnica**: `d:\2Proyectos\CONLACT\Documentacion`
- **Stack Tecnológico**:
  - **Lenguaje**: Java 25 (OpenJDK 25.0.4.1 x64)
  - **Framework**: Spring Boot 3.4.3
  - **Base de Datos**: PostgreSQL 16 (Testcontainers & Docker Desktop)
  - **Persistencia**: Spring Data JPA / Hibernate 6, Flyway Migrations
  - **Testing**: JUnit 5, Mockito 5 (`@MockitoBean`), Spring MockMvc, Testcontainers PostgreSQL, AssertJ
  - **Automatización API**: Postman v2.1.0, Newman CLI

---

## 📋 Mapeo Integral del Tablero Jira/Trello vs Suites de Prueba

| Semana / Épica | Ticket Jira | Descripción del Requerimiento | Nivel de Prueba | Clase / Suite Automatizada | Colección Postman |
|---|---|---|---|---|---|
| **Semana 3** | `[BE-12]` | Endpoints Públicos `GET /api/asociaciones` | Integración BD | [`AssociationIntegrationTest`](file:///d:/2Proyectos/CONLACT/conlact-backend/src/test/java/com/conlact/conlact_backend/AssociationIntegrationTest.java) | `BE16_S3` |
| **Semana 3** | `[BE-13]` | CRUD Admin Protegido de Asociaciones | Integración + Seguridad | [`AssociationIntegrationTest`](file:///d:/2Proyectos/CONLACT/conlact-backend/src/test/java/com/conlact/conlact_backend/AssociationIntegrationTest.java) | `BE16_S3` |
| **Semana 3** | `[BE-14]` | Gestión de URLs de Fotos de Asociaciones | Integración Relacional | [`AssociationIntegrationTest`](file:///d:/2Proyectos/CONLACT/conlact-backend/src/test/java/com/conlact/conlact_backend/AssociationIntegrationTest.java) | `BE16_S3` |
| **Semana 3** | `[BE-15]` | API de Testimonios (`testimonials`) | Unitaria + Integración | [`TestimonialControllerTest`](file:///d:/2Proyectos/CONLACT/conlact-backend/src/test/java/com/conlact/conlact_backend/controller/TestimonialControllerTest.java), [`TestimonialIntegrationTest`](file:///d:/2Proyectos/CONLACT/conlact-backend/src/test/java/com/conlact/conlact_backend/TestimonialIntegrationTest.java) | `BE16_S3` |
| **Semana 3** | `[BE-16]` | Colección Postman, Tests Unitarios y QA S3 | QA Suite Consolidada | [BE-16_QA_S3.md](file:///d:/2Proyectos/CONLACT/Documentacion/BE-16_QA_S3.md) | `BE16_S3` |
| **Semana 4** | `[BE-17]` | API `GET /api/products` con Filtros Dinámicos | Integración BD | [`CatalogComprehensiveIntegrationTest`](file:///d:/2Proyectos/CONLACT/conlact-backend/src/test/java/com/conlact/conlact_backend/CatalogComprehensiveIntegrationTest.java) | `BE21_S4` |
| **Semana 4** | `[BE-18]` | Lógica de Stock Central y Variantes JPA | Concurrencia Multi-Hilo | [`InventoryConcurrencyStressIntegrationTest`](file:///d:/2Proyectos/CONLACT/conlact-backend/src/test/java/com/conlact/conlact_backend/InventoryConcurrencyStressIntegrationTest.java) | `BE21_S4` |
| **Semana 4** | `[BE-19]` | CRUD Admin de Quesos y Stock Transaccional | Unitaria + Integración | [`AdminInventoryControllerTest`](file:///d:/2Proyectos/CONLACT/conlact-backend/src/test/java/com/conlact/conlact_backend/controller/AdminInventoryControllerTest.java), [`AdminCatalogIntegrationTest`](file:///d:/2Proyectos/CONLACT/conlact-backend/src/test/java/com/conlact/conlact_backend/AdminCatalogIntegrationTest.java) | `BE21_S4` |
| **Semana 4** | `[BE-20]` | Gestión de Galería en `product_images` | Integración Relacional | [`ProductImageIntegrationTest`](file:///d:/2Proyectos/CONLACT/conlact-backend/src/test/java/com/conlact/conlact_backend/ProductImageIntegrationTest.java) | `BE21_S4` |
| **Semana 4** | `[BE-21]` | Tests de Estrés de Inventario & QA S4 | Estrés y Concurrencia | [BE-21_QA_S4.md](file:///d:/2Proyectos/CONLACT/Documentacion/BE-21_QA_S4.md) | `BE21_S4` |
| **Semana 5** | `[BE-22]` | API de Recetas N:M `recipe_products` | Integración Relacional | [`RecipeIntegrationTest`](file:///d:/2Proyectos/CONLACT/conlact-backend/src/test/java/com/conlact/conlact_backend/RecipeIntegrationTest.java) | `BE26_S5` |
| **Semana 5** | `[BE-23]` | API de Turismo (`tourist_attractions`) | Integración Geo / BD | [`TouristAttractionIntegrationTest`](file:///d:/2Proyectos/CONLACT/conlact-backend/src/test/java/com/conlact/conlact_backend/TouristAttractionIntegrationTest.java) | `BE26_S5` |
| **Semana 5** | `[BE-24]` | Servicio SMTP de Correos con JavaMail Mock | Mocking de Red / SMTP | [`ContactSmtpMockIntegrationTest`](file:///d:/2Proyectos/CONLACT/conlact-backend/src/test/java/com/conlact/conlact_backend/ContactSmtpMockIntegrationTest.java) | `BE26_S5` |
| **Semana 5** | `[BE-25]` | `POST /api/contacto` y `contact_messages` | Unitaria + Integración | [`ContactControllerTest`](file:///d:/2Proyectos/CONLACT/conlact-backend/src/test/java/com/conlact/conlact_backend/controller/ContactControllerTest.java), [`ContactSmtpMockIntegrationTest`](file:///d:/2Proyectos/CONLACT/conlact-backend/src/test/java/com/conlact/conlact_backend/ContactSmtpMockIntegrationTest.java) | `BE26_S5` |
| **Semana 5** | `[BE-26]` | Pruebas SMTP, Turismo y QA S5 | QA Suite Consolidada | [BE-26_QA_S5.md](file:///d:/2Proyectos/CONLACT/Documentacion/BE-26_QA_S5.md) | `BE26_S5` |
| **Semana 6** | `[BE-27]` | Integración Cliente API PayPhone Business | Integración Pasarela | [`OrderTransactionalFlowIntegrationTest`](file:///d:/2Proyectos/CONLACT/conlact-backend/src/test/java/com/conlact/conlact_backend/OrderTransactionalFlowIntegrationTest.java) | `BE31_S6` |
| **Semana 6** | `[BE-28]` | Webhook Seguro `POST /api/webhooks/payphone` | Transaccional Asíncrono | [`OrderTransactionalFlowIntegrationTest`](file:///d:/2Proyectos/CONLACT/conlact-backend/src/test/java/com/conlact/conlact_backend/OrderTransactionalFlowIntegrationTest.java) | `BE31_S6` |
| **Semana 6** | `[BE-29]` | Motor Transaccional `POST /api/pedidos` | Transaccional + Finanzas | [`OrderControllerTest`](file:///d:/2Proyectos/CONLACT/conlact-backend/src/test/java/com/conlact/conlact_backend/controller/OrderControllerTest.java), [`FinancialAuditOrderTest`](file:///d:/2Proyectos/CONLACT/conlact-backend/src/test/java/com/conlact/conlact_backend/service/FinancialAuditOrderTest.java) | `BE31_S6` |
| **Semana 6** | `[BE-30]` | Reserva de Stock Temporal y Transferencias | Integración + Expiración | [`OrderTransactionalFlowIntegrationTest`](file:///d:/2Proyectos/CONLACT/conlact-backend/src/test/java/com/conlact/conlact_backend/OrderTransactionalFlowIntegrationTest.java) | `BE31_S6` |
| **Semana 6** | `[BE-31]` | QA Transaccional y Financiero S6 | Auditoría Centavos y Flujo | [BE-31_QA_S6.md](file:///d:/2Proyectos/CONLACT/Documentacion/BE-31_QA_S6.md) | `BE31_S6` |

---

## 🔬 Resumen Consolidado de Ejecución y Métricas

```
-------------------------------------------------------------------------------
 RESULTADOS DE LA SUITE CONSOLIDADA DE PRUEBAS AUTOMATIZADAS (JUNIT 5)
-------------------------------------------------------------------------------
 [PASS] com.conlact.conlact_backend.controller.TestimonialControllerTest           (3 tests)
 [PASS] com.conlact.conlact_backend.controller.AdminTestimonialControllerTest      (7 tests)
 [PASS] com.conlact.conlact_backend.TestimonialIntegrationTest                    (3 tests)
 [PASS] com.conlact.conlact_backend.AssociationIntegrationTest                    (5 tests)
 [PASS] com.conlact.conlact_backend.InventoryConcurrencyStressIntegrationTest     (2 tests)
 [PASS] com.conlact.conlact_backend.CatalogComprehensiveIntegrationTest           (4 tests)
 [PASS] com.conlact.conlact_backend.RecipeIntegrationTest                        (4 tests)
 [PASS] com.conlact.conlact_backend.TouristAttractionIntegrationTest              (2 tests)
 [PASS] com.conlact.conlact_backend.controller.RecipeControllerTest              (4 tests)
 [PASS] com.conlact.conlact_backend.ContactSmtpMockIntegrationTest                (4 tests)
 [PASS] com.conlact.conlact_backend.service.FinancialAuditOrderTest               (4 tests)
 [PASS] com.conlact.conlact_backend.OrderTransactionalFlowIntegrationTest         (5 tests)
 [PASS] com.conlact.conlact_backend.controller.OrderControllerTest                 (3 tests)
-------------------------------------------------------------------------------
 Tests run: 50, Failures: 0, Errors: 0, Skipped: 0
 BUILD SUCCESS - Cobertura Transaccional: 100%
-------------------------------------------------------------------------------
```

### Métricas Clave Certificadas:
1. **Concurrencia de Inventario**:
   - Sobrevendido (*Overselling*): **0.00%** (Cero unidades sobrevendidas bajo 30 y 15 hilos en paralelo).
   - Condición de Carrera (*Race Conditions*): **Ninguna**.
2. **Precisión Contable**:
   - Deriva IEEE-754: **0.00000000** (Uso estricto de `BigDecimal`).
   - Liquidación de Centavos: Exactitud al $0.01 en todas las combinaciones con flete y retiro en punto.
3. **Resiliencia de Pasarela y Stock**:
   - Restitución de Stock tras fallo o expiración: **100.0%** automático.
   - Seguridad de Red SMTP: **0 correos externos enviados** durante la ejecución de los tests.

---

## 🛠️ Guía Rápida de Comandos y Reproducción

### 1. Variables de Entorno y Pre-requisitos
Asegurarse de tener Docker Desktop iniciado y el JDK 25 configurado:
```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.4.1'
```

### 2. Ejecutar Todas las Suites de QA de los 4 Milestones
```powershell
cd d:\2Proyectos\CONLACT\conlact-backend
.\mvnw.cmd test -Dtest=TestimonialControllerTest,AdminTestimonialControllerTest,TestimonialIntegrationTest,AssociationIntegrationTest,InventoryConcurrencyStressIntegrationTest,CatalogComprehensiveIntegrationTest,RecipeIntegrationTest,TouristAttractionIntegrationTest,RecipeControllerTest,ContactSmtpMockIntegrationTest,FinancialAuditOrderTest,OrderTransactionalFlowIntegrationTest,OrderControllerTest
```

### 3. Ejecutar las Colecciones Postman de Forma Automatizada con Newman
```powershell
# S3: Asociaciones y Testimonios
npx -y newman run docs/postman/BE16_S3_associations_testimonials.postman_collection.json --env-var "base_url=http://localhost:8080"

# S4: Estrés y Catálogo
npx -y newman run docs/postman/BE21_S4_inventory_stress_catalog.postman_collection.json --env-var "base_url=http://localhost:8080"

# S5: Recetas, Turismo y Contacto Mock SMTP
npx -y newman run docs/postman/BE26_S5_recipes_tourism_contact_smtp.postman_collection.json --env-var "base_url=http://localhost:8080"

# S6: Motor Transaccional, Webhooks y Auditoría Financiera
npx -y newman run docs/postman/BE31_S6_transactional_financial_checkout.postman_collection.json --env-var "base_url=http://localhost:8080"
```

---

## 📂 Enlaces a los Informes Pormenorizados
- 📄 [Informe Completo Milestone S3: BE-16](file:///d:/2Proyectos/CONLACT/Documentacion/BE-16_QA_S3.md)
- 📄 [Informe Completo Milestone S4: BE-21](file:///d:/2Proyectos/CONLACT/Documentacion/BE-21_QA_S4.md)
- 📄 [Informe Completo Milestone S5: BE-26](file:///d:/2Proyectos/CONLACT/Documentacion/BE-26_QA_S5.md)
- 📄 [Informe Completo Milestone S6: BE-31](file:///d:/2Proyectos/CONLACT/Documentacion/BE-31_QA_S6.md)
