# Pruebas manuales del flujo de compra

Recorrido paso a paso para validar el sistema haciendo peticiones **solo a order**: qué petición enviar,
qué debe devolver y qué ocurre por detrás en payment y notifications. Sigue el orden de las carpetas de la
colección de Postman ([`postman/`](../postman/)).

**Antes de empezar:** `bash up.sh` en la raíz del repo, con la siembra terminada (ver el
[README](../README.md#probar-el-flujo-completo)).

## Dónde mirar lo que pasa por detrás

| Dónde | URL | Qué ves |
|---|---|---|
| Postman | — | La respuesta de order y los tests en verde o rojo |
| Mailpit | http://localhost:8025 | Los correos que recibe el comprador |
| Stripe simulado | http://localhost:8099/__admin/requests | Las llamadas que payment hizo a «Stripe» |
| RabbitMQ | http://localhost:15672 (guest/guest) | Colas y colas de mensajes muertos (`-dlq`) |
| Logs | `podman logs -f prueba-gelsa_order_1` (o `_payment_1`, `_notifications_1`) | Lo que hace cada servicio |

## Cómo validar el envío de notificaciones

order no envía el correo: publica `NotificationRequested`, notifications lo acepta (`queued`) y su barrido lo
envía **cada minuto**. Por eso un correo tarda hasta un minuto en aparecer después de que el pedido cambia de
estado. Hay tres sitios donde comprobarlo, de más visual a más detallado:

**1. Mailpit, el buzón: http://localhost:8025**

Es lo primero que hay que abrir. Cada correo enviado aparece en la bandeja de entrada:

- **Para:** `buyer@example.com` (el email del token del comprador, no uno escrito en la petición).
- **De:** `Tienda de prueba <tienda@example.com>` (el remitente que configura la siembra).
- **Asunto**, según el desenlace del pedido:

  | Desenlace | Plantilla | Asunto |
  |---|---|---|
  | `paid` | `order-paid` | Tu pedido `<id>` está pagado |
  | `paymentFailed` | `order-payment-failed` | No pudimos cobrar tu pedido `<id>` |
  | `canceled` | `order-canceled` | Tu pedido `<id>` se ha cancelado |

- **Cuerpo:** el importe, la moneda y el id del pedido; en un pago fallido, también el motivo.
- **Uno por desenlace:** aunque repitas la petición o un evento llegue dos veces, cada pedido recibe un solo
  correo por desenlace.

**2. La base de notifications: el estado de cada mensaje**

Muestra lo que Mailpit no ve: si el mensaje sigue en cola, si falló y por qué, y con qué clave de
idempotencia llegó.

```bash
podman exec prueba-gelsa_postgres_1 psql -U notifications -d notifications -c \
  "select template_code, status, failure_reason, requested_by, idempotency_key,
          requested_at::time(0), sent_at::time(0)
     from email_messages order by requested_at desc limit 10"
```

- `status`: `QUEUED` (aceptado, a la espera del barrido) → `SENDING` → `SENT` o `FAILED`.
- `requested_by`: `order`, el emisor del evento.
- `idempotency_key`: `order:<id del pedido>:<desenlace>`, la clave que impide el correo duplicado.
- `sent_at - requested_at`: como mucho un minuto, el intervalo del barrido.

**3. RabbitMQ: que el encargo llegó y no se rechazó (http://localhost:15672, guest/guest)**

En la pestaña *Queues*:

- `notifications.any-registered-system` debe estar a 0: notifications consumió los encargos de order.
- `notifications.any-registered-system-dlq` también a 0. Un mensaje aquí es un encargo **rechazado**
  (plantilla inexistente, variable que falta, emisor no registrado…) y ese correo no saldrá nunca.

Si un correo no aparece en Mailpit pasado un minuto, mira en este orden: la DLQ (rechazado), la tabla
(`QUEUED` o `FAILED`) y los logs, con `podman logs prueba-gelsa_notifications_1`.

## Paso 0 · Obtener el token del comprador

**Envía:** «0. Autenticación / Token del comprador».

**Debes ver:** `200` con un `access_token`. El test comprueba que el token trae el claim `email`
(`buyer@example.com`).

**Por qué importa:** order saca el email del pedido de este token, nunca del cuerpo. La colección lo guarda en
`{{token}}` y lo usan todas las peticiones siguientes. Dura 5 minutos: si empiezas a recibir `401`, repite
este paso.

## Paso 1 · Pago aprobado (`pm_card_visa`)

**Envía:** «1. Pago aprobado / Crear pedido» (2 camisetas y 1 taza).

**Debes ver en la respuesta (`201`):**

- `status`: `authorized` (o `pendingPayment` si payment tardó).
- `totalAmount`: **49.30** y `currency`: `EUR`. La petición solo lleva `productId` y `quantity`: **el importe
  lo calcula order** con su copia del catálogo.
- `lines`: cada línea con `sku`, `productName` y `unitPrice` congelados.
- `paymentId`: el id del cobro en payment.

**Qué ocurre por detrás:**

1. order valida los productos en su réplica, congela los precios y guarda el pedido en `pendingPayment`
   **antes** de llamar a payment.
2. order pide un token M2M a Keycloak y llama a payment (`POST /api/v1/payments`, `chargeRequestId = order:<id>`).
3. payment autoriza en Stripe (en el simulador verás un `POST /v1/payment_intents`) y publica `PaymentAuthorized`.
4. order recibe el evento y **pide la captura** (en el simulador, `POST /v1/payment_intents/{id}/capture`).
5. payment publica `PaymentCaptured`; el pedido pasa a `paid` y order encarga el correo (`NotificationRequested`).
6. notifications deja el correo en cola y su barrido (cada minuto) lo envía.

**Valida:**

- «Consultar: debe quedar paid» devuelve `status` = `paid` unos segundos después.
- En Mailpit, en **un minuto como máximo**: «Tu pedido … está pagado», con 49.30 EUR.
- En el simulador: un `payment_intents` y un `capture` para este pedido.

## Paso 2 · Pago rechazado

**Envía:** «Tarjeta rechazada (`pm_card_chargeDeclined`)».

**Debes ver (`201`, no un error HTTP):** `status` = `paymentFailed` y `failureReason` = `declined`. Un rechazo
de tarjeta es un desenlace de negocio: el pedido existe y queda registrado como fallido.

**Por detrás:** el simulador devuelve `402 card_declined`, payment lo traduce a `declined` y order encarga el
correo de pago fallido.

**Valida:** en Mailpit, «No pudimos cobrar tu pedido … (motivo: declined)». En el simulador **no** hay
`capture` para este pedido.

**Envía también:** «Sin fondos (`pm_card_chargeDeclinedInsufficientFunds`)». Lo mismo, con
`failureReason` = `insufficientFunds`.

## Paso 3 · 3DS y el comprador que abandona

**Envía:** «3. 3DS y cancelación / Crear pedido» (`pm_card_threeDSecure2Required`).

**Debes ver (`201`):** `status` = `actionRequired` y `customerAction` con `clientSecret` y `nextAction`, que es
lo que el navegador usaría para mostrar el 3DS. **Todavía no sale ningún correo.**

**Envía:** «Cancelar (el comprador abandona el 3DS)».

**Debes ver (`200`):** `status` = `canceling` o `canceled`.

**Por detrás:**

1. order guarda `canceling` antes de llamar a payment.
2. payment anula en Stripe (`POST …/cancel` en el simulador) y publica `PaymentCanceled`.
3. order pasa el pedido a `canceled` y encarga el correo.

**Valida:** «Consultar: debe quedar canceled» devuelve `canceled`, y en Mailpit llega «Tu pedido … se ha
cancelado».

**Prueba extra (manual):**

- Vuelve a enviar «Cancelar»: responde `200` con el pedido tal cual, sin volver a llamar a payment (es
  idempotente por estado).
- Cambia el id por el de un pedido `paid`: responde `409` (`ORDER_NOT_CANCELABLE`). Solo se cancela un pedido
  que espera el 3DS.

## Paso 4 · Idempotencia (el doble clic)

1. **«Crear pedido con una clave»:** `201`. La colección genera una `Idempotency-Key` nueva y la guarda.
2. **«Repetir con la MISMA clave»:** `201` con **el mismo `id`** y el pedido en su estado **actual** (ya `paid`,
   no la foto del primer intento).
3. **«Misma clave, OTRO contenido»:** `409` con `code` = `IDEMPOTENCY_KEY_REUSED`.

**Valida que no se cobró ni se avisó dos veces:**

- En el simulador hay **un solo** `POST /v1/payment_intents` para ese pedido.
- En Mailpit hay **un solo** correo de «pagado» para ese pedido.

## Paso 5 · Errores de la petición

Cada uno responde el código indicado, con su `code` en el cuerpo. **Ninguno crea un pedido ni llama a payment.**

| Petición | Respuesta |
|---|---|
| Sin token | `401` |
| Sin `Idempotency-Key` | `400` `IDEMPOTENCY_KEY_REQUIRED` |
| Producto que no existe | `422` `PRODUCT_NOT_AVAILABLE` |
| Mismo producto en dos líneas | `422` `DUPLICATE_ORDER_LINE` |
| Sin `paymentToken` ni `paymentMethodId` | `400` `PAYMENT_SOURCE_INVALID` |

## Paso 6 · Consultas

- **«Mis pedidos»:** `200` con todos los pedidos que has creado, del más nuevo al más antiguo.
- **«Pedido que no existe»:** `404` `ORDER_NOT_FOUND`.

## Al terminar: comprueba que nada se perdió

En RabbitMQ (pestaña *Queues*), las colas de mensajes muertos tienen que estar **a 0**:

- `order.payment-dlq`
- `order.catalog-dlq`
- `notifications.any-registered-system-dlq`

Un mensaje ahí significaría que un evento se rechazó y no se aplicó.

Hay tres colas que crecen sin que nadie las lea: `paymentEvents`, `notificationRequests` y
`notificationEvents`. Las crea algún servicio con el nombre del canal del diseño, y es normal que acumulen.

## Opcional · Pruebas de resiliencia

Demuestran lo que explica [ARCHITECTURE.md § 9, «Qué pasa cuando algo se cae»](../ARCHITECTURE.md#9-qué-pasa-cuando-algo-se-cae).
No forman parte de la colección.

**Si notifications cae, el pedido avanza igual**

1. `podman stop prueba-gelsa_notifications_1`
2. Crea un pedido con `pm_card_visa`: llega a `paid` igual.
3. `podman start prueba-gelsa_notifications_1`: el `NotificationRequested` esperaba en la cola, y el correo
   llega al minuto de que vuelva.

**Si payment cae, order no se rompe**

1. `podman stop prueba-gelsa_payment_1`
2. Crea un pedido: `201` con `pendingPayment`. No es un error: es el estado intermedio honesto.
3. `podman start prueba-gelsa_payment_1`
4. Pasados unos minutos, el barrido de order pregunta a payment por ese cobro. Como payment nunca lo registró,
   el pedido pasa a `paymentFailed` con `failureReason` = `notReceived`, y sale el correo de pago fallido.

## Empezar desde cero

`bash down.sh -v && bash up.sh` borra la base (pedidos, cobros y correos) y vuelve a sembrar.
