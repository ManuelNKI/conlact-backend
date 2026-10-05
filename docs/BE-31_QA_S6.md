# [BE-31] QA Transaccional y Financiero S6

## 1. Resumen Ejecutivo
Para el Milestone de la Semana 6 (S6), se implementó la certificación integral del **flujo transaccional de compras**, la **auditoría matemática de centavos y redondeos financieros**, y la **resolución de inventario ante webhooks de pasarela de pagos (PayPhone)**:
1. **Flujo de Compra Transaccional Completo**:
   - Orden creada en `/api/pedidos` o `/api/orders`.
   - **Reserva inmediata de stock físico** en base de datos (`inventory_reservations` con estado `active` y descuento atómico sobre `product_variants.stock`).
   - Notificación por **Webhook de Pago**:
     - **Caso Éxito (`Approved` / `statusCode = 3`)**: Confirmación del pedido (`status: paid`, `payment_status: approved`), consolidación definitiva de la reserva (`status: consumed`), stock permanece descontado sin doble deducción.
     - **Caso Fallido / Rechazado (`Rejected` / `statusCode = 2`)**: Cancelación del pedido (`status: cancelled`, `payment_status: rejected`), liberación de la reserva (`status: released`), y **restitución física automática del stock** a la variante en base de datos (`variant.addStock(...)`), garantizando 0 fuga de stock.
2. **Auditoría Financiera de Centavos**:
   - Validación de operaciones en `BigDecimal` con precisión estricta a 2 decimales ($0.01).
   - Mitigación de distorsiones de punto flotante binario IEEE 754 (ej. $0.10 + $0.20 = $0.30 exacto sin residuos `0.30000000000000004`).
   - Verificación de fórmulas de facturación: `total = subtotal + costo_envio`.
   - Certificación de gratuidad en retiros en punto (`flete = 0.00`).

---

## 2. Matriz de Pruebas Automatizadas

| Clase de Prueba | Tipo | Casos Cubiertos | Resultado |
|---|---|---|---|
| [`FinancialAuditOrderTest`](file:///d:/2Proyectos/CONLACT/conlact-backend/src/test/java/com/conlact/conlact_backend/service/FinancialAuditOrderTest.java) | Unitario (Matemático / Financiero) | **1. Auditoría multilínea**: 3 variantes con precios centesimales ($3.45 x 3 = $10.35, $4.99 x 2 = $9.98, $2.35 x 4 = $9.40 -> Subtotal $29.73 + Flete $2.50 = Total $32.23 exacto).<br>**2. Prevención de deriva IEEE 754**: Precios decimales $0.10 y $0.20 acumulados sin error de mantisa.<br>**3. Retiro en punto (Pickup)**: Costo de flete estrictamente $0.00.<br>**4. Validaciones de zona inactiva**: Rechazo `400 BAD REQUEST` ante zonas no disponibles. | **PASS** |
| [`OrderTransactionalFlowIntegrationTest`](file:///d:/2Proyectos/CONLACT/conlact-backend/src/test/java/com/conlact/conlact_backend/OrderTransactionalFlowIntegrationTest.java) | Integración Transaccional (PostgreSQL) | **Flujo 1 (Aprobado)**: Orden de 5 unidades -> Stock de 80 pasa a 75 -> Webhook PayPhone `status: Approved` -> Orden pasa a `paid`, reserva pasa a `consumed`, stock permanece en 75.<br>**Flujo 2 (Rechazado & Reversión)**: Orden de 10 unidades -> Stock pasa a 65 temporalmente -> Webhook PayPhone `status: Rejected` -> Orden pasa a `cancelled`, reserva pasa a `released`, **stock se restituye automáticamente de 65 a 75**.<br>**Flujo 3 (Validaciones Webhook)**: Payload vacío o sin `clientTransactionId` retorna 400; orden inexistente retorna 404.<br>**Flujo 4 (Expiración Temporal de Reservas - BE-30)**: Simulación de vencimiento de tiempo límite de reserva (15 min) -> ejecución de `releaseExpiredReservations()` -> cancelación automática de orden y restitución del 100% del stock físico.<br>**Flujo 5 (Transferencias Bancarias - BE-30)**: Creación de pedido con `metodo_pago: "transferencia"` -> estado `pending`, reserva activa sin redirección a PayPhone. | **PASS** |

---

## 3. Colección Postman Versionada
- **Archivo**: [`conlact-backend/docs/postman/BE31_S6_transactional_financial_checkout.postman_collection.json`](file:///d:/2Proyectos/CONLACT/conlact-backend/docs/postman/BE31_S6_transactional_financial_checkout.postman_collection.json)
- **Variables de Entorno**:
  - `{{base_url}}`: `http://localhost:8080`
  - `{{admin_token}}`: Token Bearer JWT de admin
  - `{{variant_id}}`: `ba000000-0000-0000-0000-000000000001`
  - `{{shipping_zone_id}}`: `f0000000-0000-0000-0000-000000000001`
  - `{{order_id}}`: Capturado automáticamente en la creación del pedido
  - `{{order_number}}`: Número de pedido autoincremental para tracking

### Pruebas Postman Automatizadas Incluidas
1. **Crear pedido a domicilio con reserva de stock**: Valida código `201 CREATED`, scripts automáticos de auditoría de centavos: `total === subtotal + flete`. Guarda `order_id` y `order_number`.
2. **Crear pedido pickup**: Valida código `201 CREATED`, verifica `flete === 0.00` y `total === subtotal`.
3. **Seguimiento de orden por número de pedido**: Valida `GET /api/pedidos/track/{{order_number}}`.
4. **Simulación Webhook Aprobado**: `POST /api/webhooks/payphone`, valida confirmación transaccional: `order_status === "paid"` y `payment_status === "approved"`.
5. **Simulación Webhook Rechazado**: `POST /api/webhooks/payphone`, valida cancelación transaccional: `order_status === "cancelled"` y `payment_status === "rejected"`.
6. **Validación 400**: Payload sin datos requeridos.

---

## 4. Comando de Ejecución
```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.4.1'
.\mvnw.cmd test -Dtest=FinancialAuditOrderTest,OrderTransactionalFlowIntegrationTest
```
