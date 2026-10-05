# Documentación de Pruebas de Calidad (QA) y Colecciones Postman - Backend CONLAC-T

Este directorio contiene la documentación técnica exhaustiva, matrices de casos de prueba y especificaciones de las colecciones Postman implementadas exclusivamente para el **Backend de CONLAC-T** (`conlact-backend`), cubriendo los cuatro hitos de aseguramiento de calidad (QA): **[BE-16]**, **[BE-21]**, **[BE-26]** y **[BE-31]**.

---

## 📌 Rama de Trabajo Git
- **Rama Feature**: `feature/BE-16-21-26-31-qa-test-suites`
- **Repositorio**: `d:\2Proyectos\CONLACT\conlact-backend`
- **Entorno de Ejecución**: Java 25 (OpenJDK 25.0.4.1), Spring Boot 4.1.1, PostgreSQL 16 (Testcontainers & Docker Desktop).

---

## 📋 Resumen de Hitos Implementados

| Ticket | Milestone | Área de Dominio | Documentación Detallada | Colección Postman Versionada |
|---|---|---|---|---|
| **[BE-16]** | **QA S3** | Asociaciones de Productores & Testimonios de Clientes | [BE-16_QA_S3.md](file:///d:/2Proyectos/CONLACT/Documentacion/BE-16_QA_S3.md) | [`BE16_S3_associations_testimonials.postman_collection.json`](file:///d:/2Proyectos/CONLACT/conlact-backend/docs/postman/BE16_S3_associations_testimonials.postman_collection.json) |
| **[BE-21]** | **QA S4** | Tests de Estrés de Inventario & Cobertura de Catálogo | [BE-21_QA_S4.md](file:///d:/2Proyectos/CONLACT/Documentacion/BE-21_QA_S4.md) | [`BE21_S4_inventory_stress_catalog.postman_collection.json`](file:///d:/2Proyectos/CONLACT/conlact-backend/docs/postman/BE21_S4_inventory_stress_catalog.postman_collection.json) |
| **[BE-26]** | **QA S5** | Recetas, Turismo & Formulario con SMTP Mockeado | [BE-26_QA_S5.md](file:///d:/2Proyectos/CONLACT/Documentacion/BE-26_QA_S5.md) | [`BE26_S5_recipes_tourism_contact_smtp.postman_collection.json`](file:///d:/2Proyectos/CONLACT/conlact-backend/docs/postman/BE26_S5_recipes_tourism_contact_smtp.postman_collection.json) |
| **[BE-31]** | **QA S6** | Flujo Transaccional de Compra, Webhooks & Auditoría Financiera | [BE-31_QA_S6.md](file:///d:/2Proyectos/CONLACT/Documentacion/BE-31_QA_S6.md) | [`BE31_S6_transactional_financial_checkout.postman_collection.json`](file:///d:/2Proyectos/CONLACT/conlact-backend/docs/postman/BE31_S6_transactional_financial_checkout.postman_collection.json) |

---

## 🧪 Matriz Global de Pruebas Automatizadas

```
-------------------------------------------------------
 T E S T S   S U M M A R Y
-------------------------------------------------------
[PASS] TestimonialControllerTest                  (4 tests)
[PASS] AdminTestimonialControllerTest             (5 tests)
[PASS] TestimonialIntegrationTest                 (3 tests)
[PASS] AssociationIntegrationTest                 (7 tests - incl. fotos BE-14)
[PASS] InventoryConcurrencyStressIntegrationTest  (2 tests - 30 & 15 hilos concurrentes)
[PASS] CatalogComprehensiveIntegrationTest        (4 tests)
[PASS] RecipeIntegrationTest                     (4 tests - incl. recipe_products BE-22)
[PASS] TouristAttractionIntegrationTest           (4 tests)
[PASS] RecipeControllerTest                       (4 tests)
[PASS] ContactSmtpMockIntegrationTest             (4 tests)
[PASS] FinancialAuditOrderTest                    (4 tests)
[PASS] OrderTransactionalFlowIntegrationTest      (5 tests - incl. expiración y transferencias BE-30)
-------------------------------------------------------
Total Tests Ejecutados en Suite Consolidada: 50
Tests Exitosos: 50
Fallos / Errores: 0
Estado: BUILD SUCCESS (100% de efectividad)
-------------------------------------------------------
```

---

## 🚀 Guía de Ejecución Rápida

### 1. Requisitos Previos
1. **JDK 25** instalado en `C:\Program Files\Java\jdk-25.0.4.1`.
2. **Docker Desktop** activo (para levantar los contenedores PostgreSQL de Testcontainers y `conlact-postgres-dev`).

### 2. Ejecución de la Suite Completa de Pruebas
Desde la raíz del backend (`d:\2Proyectos\CONLACT\conlact-backend`):
```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.4.1'
.\mvnw.cmd test '-Dtest=AssociationIntegrationTest,TestimonialControllerTest,AdminTestimonialControllerTest,InventoryConcurrencyStressIntegrationTest,CatalogComprehensiveIntegrationTest,RecipeIntegrationTest,TouristAttractionIntegrationTest,RecipeControllerTest,ContactSmtpMockIntegrationTest,FinancialAuditOrderTest,OrderTransactionalFlowIntegrationTest'
```

### 3. Ejecución por Hito Específico
- **Solo S3 ([BE-16])**:
  ```powershell
  .\mvnw.cmd test '-Dtest=AssociationIntegrationTest,TestimonialControllerTest,AdminTestimonialControllerTest'
  ```
- **Solo S4 ([BE-21])**:
  ```powershell
  .\mvnw.cmd test '-Dtest=InventoryConcurrencyStressIntegrationTest,CatalogComprehensiveIntegrationTest'
  ```
- **Solo S5 ([BE-26])**:
  ```powershell
  .\mvnw.cmd test '-Dtest=RecipeIntegrationTest,TouristAttractionIntegrationTest,RecipeControllerTest,ContactSmtpMockIntegrationTest'
  ```
- **Solo S6 ([BE-31])**:
  ```powershell
  .\mvnw.cmd test '-Dtest=FinancialAuditOrderTest,OrderTransactionalFlowIntegrationTest'
  ```

---

## 📬 Colecciones Postman y Automatización
Todas las colecciones están ubicadas en `conlact-backend/docs/postman/` en formato estándar **v2.1.0** de Postman:
1. Incluyen variables de entorno configurables (`base_url`, `admin_token`, slugs, UUIDs).
2. Tienen scripts de assertions en JavaScript en la pestaña **Tests** para verificar de forma desatendida códigos de estado HTTP (`200`, `201`, `204`, `400`, `404`), tipos de datos y esquemas de respuesta.
3. Compatibles con **Newman CLI** para pipelines CI/CD:
   ```bash
   newman run docs/postman/BE16_S3_associations_testimonials.postman_collection.json --env-var "base_url=http://localhost:8080"
   ```
