# BE-24 — Correo SMTP asíncrono

`EmailNotificationService` implementa `IEmailService`, usa `JavaMailSender` y renderiza HTML con Thymeleaf. Es un servicio interno para contacto y pedidos; no expone un endpoint público de envío de correos.

## Uso desde otro servicio de Spring

Inyectar `IEmailService` por constructor y llamar:

```java
CompletableFuture<Void> delivery = emailService.sendEmail(new TemplatedEmail(
        "cliente@example.com",
        "Recibimos tu pedido CON-1001",
        EmailTemplate.ORDER_RECEIVED,
        Map.of("customerName", "María", "orderNumber", "CON-1001", "total", new BigDecimal("12.50"))
));
```

La llamada se ejecuta mediante `@Async("mailTaskExecutor")` en un hilo `mail-*`. No llamar `get()` ni `join()` desde la petición HTTP: bloquearían al controlador. El llamador puede registrar `whenComplete(...)` para observar el resultado. El futuro solo termina satisfactoriamente cuando `JavaMailSender.send` termina; un fallo SMTP conserva su causa en `EmailDeliveryException`.

El método debe invocarse a través del bean inyectado de Spring. Una llamada interna a un método de la misma instancia no pasa por el proxy asíncrono. Contacto/pedidos deben disparar la notificación después de confirmar la transacción de negocio, para no notificar operaciones revertidas.

El executor tiene 2 hilos base, máximo 4 y cola de 100 tareas. Si se satura rechaza nuevas tareas; el llamador debe tratar ese rechazo. La cola vive en memoria y no proporciona persistencia, reintentos automáticos ni garantía de entrega ante un cierre inesperado. En el cierre normal se esperan hasta 15 segundos para completar tareas pendientes.

## Plantillas

| Enum | Uso | Variables obligatorias | Opcionales |
| --- | --- | --- | --- |
| `NOTIFICATION` | Aviso general | `title`, `message` | `recipientName`, `actionUrl`, `actionLabel` |
| `CONTACT_NOTIFICATION` | Contacto al administrador | `contactName`, `contactEmail`, `message` | `phone`, `contactSubject` |
| `ORDER_RECEIVED` | Pedido recibido al comprador | `customerName`, `orderNumber`, `total` | `actionUrl` |
| `ORDER_STATUS` | Pago verificado o despacho | `customerName`, `orderNumber`, `status` | `message`, `actionUrl` |

Los HTML están en `src/main/resources/templates/email`. El enum impide seleccionar rutas arbitrarias de plantillas. Se usa `th:text`, que escapa contenido, y codificación UTF-8. La solicitud copia sus variables antes de encolarse. Valida destinatario, asunto sin saltos de línea, variables obligatorias y enlaces HTTP/HTTPS. Los logs no imprimen claves SMTP, destinatarios ni contenido del mensaje.

## Configuración

La aplicación arranca sin proveedor configurado. `MAIL_ENABLED=false` por defecto hace que una solicitud de envío termine con error explícito, sin contactar SMTP. No significa que el correo se haya enviado.

Configurar las variables en `.env` para un proveedor SMTP con STARTTLS:

```dotenv
MAIL_ENABLED=true
MAIL_FROM=notificaciones@tu-dominio.example
MAIL_FROM_NAME=CONLAC-T
SMTP_HOST=smtp.tu-proveedor.example
SMTP_PORT=587
SMTP_USERNAME=usuario_smtp
SMTP_PASSWORD=clave_privada
SMTP_AUTH=true
SMTP_STARTTLS_ENABLED=true
SMTP_SSL_ENABLED=false
```

Para SMTP con TLS implícito, usar el puerto indicado por el proveedor (habitualmente 465), `SMTP_SSL_ENABLED=true` y `SMTP_STARTTLS_ENABLED=false`. Para un servidor local sin autenticación, usar sus datos y dejar ambas opciones TLS y `SMTP_AUTH` en false. No combinar TLS implícito con STARTTLS. Los timeouts de conexión, lectura y escritura son 5 segundos; habilitar STARTTLS también lo exige.

El remitente debe estar permitido por el proveedor. `.env` está ignorado por Git; no incluir credenciales en código, plantillas, Postman ni commits. Este módulo no envía correos automáticamente al crear testimonios.

## Integración de contacto tras unir develop

BE-25 invoca `IEmailService.sendContactNotificationToAdmin` después de confirmar la transacción que guarda el contacto. La implementación usa `mailTaskExecutor`, la plantilla `CONTACT_NOTIFICATION` y JavaMailSender real. Una copia del mensaje evita compartir la entidad administrada por JPA con el hilo de correo. El asunto SMTP es fijo; el asunto del formulario se muestra escapado dentro del HTML.

El destinatario se configura con `MAIL_ADMIN_RECIPIENT`; si se omite, se usa `MAIL_FROM`. La cuenta Gmail ya configurada puede recibir estas notificaciones sin agregar otra variable. Con correo deshabilitado se omite la notificación, sin simular una entrega exitosa. Los fallos SMTP se registran por ID/tipo de error; un rechazo al encolar tampoco invalida el contacto guardado. Los flujos de pedidos todavía deben invocar el servicio desde su integración correspondiente.

## Verificación

Las pruebas usan el proxy real de `@Async`, el mismo motor Spring/Thymeleaf del backend y un `JavaMailSender` simulado. Comprueban que el llamador continúa mientras SMTP está bloqueado, las cuatro plantillas, UTF-8, escape de HTML, copia de variables, propagación de fallos y estado deshabilitado. No se enviaron correos a destinatarios reales.

El 6 de octubre de 2026 pasó la suite local completa: **185 pruebas, 0 fallos, 0 errores**, con JDK 25.0.4 y PostgreSQL 16 de Testcontainers. El servicio de correo aporta 9 casos, incluidos los 4 de renderizado parametrizado. Se excluyó únicamente `SupabaseLiveConfigurationTest`, que necesita credenciales reales y escribe en Supabase remoto.

También se comprobó el arranque local de Spring en el puerto 8080 con la configuración SMTP deshabilitada por defecto. Las pruebas de entrega usan el servidor simulado; la entrega real debe verificarse después de configurar el proveedor.

La integración de `develop` del 6 de octubre pasó **292 pruebas, 0 fallos, 0 errores**. Se conservaron los casos de ambas ramas y se adaptaron los contratos de las pruebas nuevas a los campos de moderación. Las integraciones fuerzan `app.mail.enabled=false` mediante propiedades dinámicas, incluso si `.env` habilita Gmail; las pruebas de correo usan JavaMailSender simulado. Se excluyó nuevamente `SupabaseLiveConfigurationTest`.

Referencias: [JavaMailSender y timeouts](https://docs.spring.io/spring-boot/4.1-SNAPSHOT/reference/io/email.html), [ejecución asíncrona](https://docs.spring.io/spring-framework/reference/integration/scheduling.html), [Thymeleaf y escape de contenido](https://www.thymeleaf.org/doc/tutorials/3.1/usingthymeleaf.html).
