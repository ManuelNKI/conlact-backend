# [BE-21] Tests de Estrés de Inventario & QA S4

## 1. Resumen Ejecutivo
Para el Milestone de la Semana 4 (S4), se implementó una suite avanzada de pruebas de estrés y concurrencia multi-hilo, orientada a certificar la integridad transaccional del inventario frente a condiciones de alta concurrencia (compras simultáneas de variantes con stock limitado). Se garantiza la **ausencia total de race conditions**, **cero overselling** (sobreventa), y stock no negativo (`stock >= 0`). Adicionalmente, se certifica la cobertura de pruebas de catálogo público y la versión correspondiente de la colección Postman S4.

---

## 2. Alcance Técnico
- **Pruebas de Estrés Concurrente**: Simulación de hilos concurrentes utilizando `ExecutorService`, `CountDownLatch`, `AtomicInteger`, y `CompletableFuture`.
- **Estrategias de Concurrencia Validadas**:
  1. **Control de Versión Optimista (`@Version Long version`)**: Previene actualizaciones perdidas (*lost updates*) en la entidad `ProductVariant` ante modificaciones administrativas concurrentes.
  2. **Bloqueo Pesimista / Transaccional (`PESSIMISTIC_WRITE` / `SELECT FOR UPDATE` & Constraints)**: Descuento seguro de stock directo y a través de `OrderService.createOrder` garantizando que transacciones competidoras reciban `InsufficientStockException` o conflicto cuando el stock se agote, sin corromper el balance físico.
- **Rutas de Catálogo**: Validación de filtros por disponibilidad (`?disponible=true`), búsqueda full-text (`?busqueda=queso`), consulta por slug sembrado y detección automática del umbral `is_low_stock`.

---

## 3. Matriz de Pruebas Automatizadas

| Clase de Prueba | Tipo | Casos Cubiertos | Resultado |
|---|---|---|---|
| [`InventoryConcurrencyStressIntegrationTest`](file:///d:/2Proyectos/CONLACT/conlact-backend/src/test/java/com/conlact/conlact_backend/InventoryConcurrencyStressIntegrationTest.java) | Estrés / Concurrencia (PostgreSQL) | **Caso 1**: 30 hilos concurrentes compitiendo por 10 unidades vía `ProductVariantService.deductStock`. Resultado exacto: 10 peticiones exitosas, 20 rechazadas, stock final = 0.<br>**Caso 2**: 15 hilos concurrentes compitiendo por 4 unidades vía `OrderService.createOrder`. Resultado exacto: 4 órdenes creadas, 11 rechazadas por falta de stock, stock final = 0, cero compras duplicadas. | **PASS** |
| [`CatalogComprehensiveIntegrationTest`](file:///d:/2Proyectos/CONLACT/conlact-backend/src/test/java/com/conlact/conlact_backend/CatalogComprehensiveIntegrationTest.java) | Integración (PostgreSQL) | Rutas duales `/api/productos` y `/api/products`. Detalle por slug sembrado `queso-fresco-artesanal-el-lindero`. Filtro de disponibilidad (`disponible=true`), búsqueda textual (`busqueda=queso`), y cálculo dinámico de `bajo_umbral_stock`. | **PASS** |
| [`AdminInventoryControllerTest`](file:///d:/2Proyectos/CONLACT/conlact-backend/src/test/java/com/conlact/conlact_backend/controller/AdminInventoryControllerTest.java) | Unitario (MockMvc) | Endpoints `/api/admin/inventario/bajo-stock`, descuento con versión optimista y reposición de stock. | **PASS** |

---

## 4. Colección Postman Versionada
- **Archivo**: [`conlact-backend/docs/postman/BE21_S4_inventory_stress_catalog.postman_collection.json`](file:///d:/2Proyectos/CONLACT/conlact-backend/docs/postman/BE21_S4_inventory_stress_catalog.postman_collection.json)
- **Variables de Entorno**:
  - `{{base_url}}`: `http://localhost:8080`
  - `{{admin_token}}`: Token Bearer JWT de admin
  - `{{product_slug}}`: `queso-fresco-artesanal-el-lindero`
  - `{{variant_id}}`: `ba000000-0000-0000-0000-000000000001`
  - `{{variant_version}}`: Versión optimista para control de concurrencia

### Pruebas Postman Automatizadas Incluidas
1. **Listar catálogo [ES / EN]**: Valida código `200 OK`, lista de productos con variantes anidadas.
2. **Detalle por slug sembrado**: Valida coincidencia exacta de slug, campos descriptivos y guarda `variant_id`.
3. **Filtro disponibilidad**: Certifica que cada producto retornado posea `tiene_stock === true`.
4. **Búsqueda textual**: Valida coincidencia parcial de texto en nombres o descripciones.
5. **Inventario bajo stock**: Consulta administrativa de variantes que requieren reposición urgente.
6. **Descuento de stock con versión optimista**: Valida descuento y actualiza `variant_version`.
7. **Reposición de stock**: Incrementa unidades físicas y actualiza auditoría.

---

## 5. Comando de Ejecución
```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.4.1'
.\mvnw.cmd test -Dtest=InventoryConcurrencyStressIntegrationTest,CatalogComprehensiveIntegrationTest,AdminInventoryControllerTest
```
