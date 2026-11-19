# order — Documento de diseño

> specs/order v0.1.0. Diseño cerrado; el porqué de las decisiones se entrevistó al cerrarlo.

## 1. Propósito y alcance

`order` recibe los pedidos de los compradores, calcula su importe con la copia local del catálogo,
pide el cobro a **payment**, sigue su desenlace y se lo notifica al comprador a través de
**notifications**. Sirve a compradores autenticados que lo llaman desde una SPA: cada uno crea,
consulta, lista y anula **sus** pedidos.

El catálogo de productos no es suyo: mantiene una **réplica** de `catalog` alimentada por eventos.
Mientras `catalog` no exista, un emisor de pruebas publica esos eventos con el contrato que este
diseño propone.

Fuera de alcance, a propósito: devoluciones, inventario y stock, gastos de envío, descuentos e
impuestos, y cualquier consulta de backoffice o de otros servidores (ver § 7).

## 2. Modelo de dominio

### Value types

| Tipo | Significado |
|---|---|
| `CustomerRef` | Referencia opaca del comprador: el `sub` de su token (1–200). Es el `customerRef` con el que se cobra en payment. |
| `EmailAddress` | Dirección de correo del comprador, tal como la trae su token. |
| `Amount` | Importe en unidades mayores, hasta 3 decimales, como acepta payment. Un decimal de más se **rechaza** (`scalePolicy: reject`), nunca se redondea. |
| `CurrencyCode` | ISO 4217 en mayúsculas. |
| `OrderStatus` | `pendingPayment`, `actionRequired`, `authorized`, `paid`, `canceling`, `canceled`, `paymentFailed`. |
| `PaymentStatus`, `PaymentFailureReason` | El vocabulario del contrato de payment, copiado tal cual. |
| `OrderLineRequest` | `{ productId, quantity }`: una línea tal como la pide el comprador. El precio **no viaja**. |
| `NotificationVariable` | `{ name, value }`: la firma que exige notifications para las variables de su plantilla. |

### Agregados

- **`Order`** (raíz) **+ `OrderLine`**. El pedido y sus líneas se crean juntos y no cambian por
  separado. Cada línea **congela** `sku`, `productName` y `unitPrice` al crear el pedido: el precio
  cobrado es el que vio el comprador, aunque catalog lo cambie después.
- **`Product`**, un agregado por sí solo. Es la réplica de catalog y no es fuente de verdad: copia solo
  lo que `createOrder` lee (`sku`, `name`, `price`, `currency`), más `sourceVersion` para descartar
  eventos viejos y `retired` como **lápida** de un producto retirado.

### Campos de `Order`

- `customerRef`, `customerEmail`: **personal**. No salen en ningún log ni traza.
- `totalAmount`: **computed**, `Σ unitPrice × quantity` de las líneas.
- `lineCount`: **computed**, el número de líneas.
- `id`, `createdAt`, `updatedAt`: **generated**. `createdAt` y `updatedAt` son contrato: salen en la
  respuesta y ordenan el listado.
- `paymentId`: el id del cobro en payment. `null` significa solo una cosa: payment nunca registró el
  cobro.
- `customerAction`: la acción 3DS, un JSON opaco. Existe solo en `actionRequired` y mientras se anula
  desde ahí (`canceling`).
- `failureReason`: solo en `paymentFailed`.
- `chargeOrderAwaitingSince`, `captureOrderPaymentAwaitingSince`, `cancelOrderPaymentAwaitingSince`,
  `actionRequiredSince`: **marcas internas** de los barridos. Están excluidas de toda respuesta.

### Ciclo de vida de `Order`

```
pendingPayment → actionRequired | authorized | paymentFailed | canceled
actionRequired → authorized | paymentFailed | canceling | canceled
authorized     → paid | canceled
canceling      → canceled | actionRequired | authorized | paymentFailed
paid, canceled, paymentFailed: terminales
```

Los checks de la entidad hacen cumplir:

- `customerAction` existe en `actionRequired` y no existe fuera de `actionRequired` o `canceling`;
- `failureReason` existe solo en `paymentFailed`;
- `paymentId` es obligatorio desde `actionRequired`;
- `totalAmount` es mayor que 0.

## 3. Invariantes y reglas clave

- Un producto aparece como mucho en una línea del pedido (`DUPLICATE_ORDER_LINE`).
- Todos los productos del pedido comparten moneda, que pasa a ser la del pedido (`CURRENCY_MISMATCH`).
- El precio de una línea no cambia después de crear el pedido.
- El pedido se confirma en `pendingPayment` **antes** de llamar a payment, y la llamada nunca ocurre
  dentro de la transacción. Lo mismo vale para la anulación (`canceling`) y la captura.
- El cobro se pide con `chargeRequestId = "order:<id del pedido>"`. El prefijo permite ignorar los
  eventos de `paymentEvents` que pertenecen a otros clientes de payment.
- **Un rechazo del cobro no es un error HTTP.** `createOrder` responde `201` con el pedido en
  `paymentFailed`.
- Tras cada respuesta de payment se **relee** el pedido. Si un evento ya lo movió, se responde con el
  estado actual, sin `409`.
- El `email` sale del claim del token, nunca del cuerpo, así que no se pueden encargar correos a
  direcciones ajenas.
- Un evento de producto con una versión menor o igual que la guardada se descarta. Una retirada deja
  una lápida que un alta vieja no resucita. Un precio menor o igual que 0 deja el producto sin poder
  pedirse.

## 4. Qué hace

### Para compradores (`/api/v1`, token de usuario)

| Operación | Endpoint | Qué hace |
|---|---|---|
| `createOrder` | `POST /orders` → `201` + `Location` | Crea el pedido, congela los precios, pide el cobro y responde en el estado que alcanzó. **`Idempotency-Key` obligatoria**: 24 h, una por comprador. Repetir devuelve el pedido en su estado actual. |
| `getOrder` | `GET /orders/{id}` | Un pedido propio. Uno ajeno responde `404`. Sin caché, porque el comprador consulta el estado mientras espera el pago. |
| `listMyOrders` | `GET /orders` | Paginado (20 por defecto, 100 como máximo), del más reciente al más antiguo. Admite `?status=` y `?sort=createdAt,asc\|desc`. Sin caché. |
| `cancelOrder` | `POST /orders/{id}/cancel` | Anula un pedido que espera el 3DS. Es idempotente por estado. |

No hay superficie servidor-a-servidor: ningún otro servidor consume este servicio.

### Desenlaces del cobro (eventos de payment)

`applyPaymentAuthorized` (pide la captura), `applyPaymentActionRequired`, `applyPaymentCaptured`
(pasa a `paid` y pide el correo), `applyPaymentFailed`, `applyPaymentCanceled` y
`applyPaymentActionRejected`.

Cada evento:

- se deduplica por `metadata.eventId`;
- solo actúa si el pedido está en uno de los estados de origen de su transición, y en cualquier otro
  no hace nada;
- se reintenta hasta 5 intentos y después va a la DLQ.

### Barrido `reconcileStalledOrders` (cada 5 minutos)

Rescata cuatro esperas:

- **`pendingPayment` vencido**: consulta a payment por la clave del cobro. Si payment no lo tiene,
  el pedido pasa a `paymentFailed(notReceived)`.
- **`authorized` sin captura confirmada**: vuelve a pedir la captura.
- **`canceling` sin anulación confirmada**: vuelve a pedir la anulación.
- **`actionRequired` de más de 25 h**: consulta a payment.

Procesa 100 pedidos por ciclo, sin solapar ciclos. Con varias instancias, cada una reclama un lote
disjunto.

### Réplica

`upsertProduct` y `removeProduct`, disparadas por `ProductUpserted` y `ProductRetired` de catalog.

## 5. Fronteras e integraciones

### Dependencias

| Proveedor | Contrato | Tipo | Qué y por qué |
|---|---|---|---|
| **catalog** | sin publicar (modo degradado) | need `productPricing`, **replicated** | Precio, moneda, sku y nombre para `createOrder`. Replicado porque el diseñador acepta cobrar el precio de la copia: un cambio tarda segundos en llegar, y es el precio que vio el comprador. `onMiss: fail` → `PRODUCT_NOT_AVAILABLE`. Sin rescate por HTTP, porque catalog no tiene API. |
| **payment** | 0.1.4 | activación `chargeOrder` (`requestCharge`) | Cobrar. El desenlace llega diferido por eventos. Si payment no contesta → `degrade`: el pedido queda en `pendingPayment` y el barrido lo resuelve. |
| | | activación `captureOrderPayment` (`capturePayment`) | Captura automática al autorizarse. Si payment no contesta, el barrido reintenta. |
| | | activación `cancelOrderPayment` (`cancelPayment`) | Anular a petición del comprador. Si payment no contesta, el pedido queda en `canceling` y el barrido reintenta. |
| | | need `chargeStatus`, **on-demand** (`getPaymentByChargeRequest`) | Solo lo usa el barrido para un cobro en duda. Si payment no contesta, se consulta en el siguiente ciclo. |
| **notifications** | 0.3.0 | activación `notifyOrderOutcome`, publica `NotificationRequested`, `awaits: nothing` | Correo de `order-paid`, `order-payment-failed` y `order-canceled` al comprador. `idempotencyKey = "order:<id>:<desenlace>"`. |

### Canales

| Dirección | Canal | Eventos |
|---|---|---|
| Consume | `paymentEvents` | 6 eventos de payment |
| Consume | `catalogEvents` | 2 eventos de catalog |
| Publica | `notificationRequests` (externo, de notifications) | `NotificationRequested`, por **outbox** |

Todos los canales llevan envoltura Keel.

### HTTP saliente

Cliente `payment`: OAuth2 client credentials con `payment:charge` y `payment:read`. Timeout de 5 s,
3 intentos exponenciales ante timeout, 5xx o fallo de conexión, y circuit breaker al 50 % sobre 20
llamadas, abierto 30 s.

### Persistencia

- Modelo relacional, frontera **per-aggregate** y bloqueo optimista en toda raíz.
- Auditoría: tiempos `declared` y sin autoría.
- Índices por `(customerRef, createdAt)` y por `(status, <marca>)` para cada barrido.

### Seguridad

- OIDC. El comprador es el `sub` del token. No hay roles: cualquier usuario autenticado compra.
- `ownership` sobre `Order`:
  - las lecturas se acotan por construcción, y un pedido ajeno se ve como inexistente (`404`);
  - `cancelOrder` sobre un pedido ajeno responde `403 ORDER_NOT_OWNED`.
- CORS para la SPA.

## 6. Decisiones de diseño (qué / por qué)

| Decisión | Elegido | Descartado | Por qué |
|---|---|---|---|
| Emulación de catalog | Réplica alimentada por eventos | Tabla propia con operaciones de administración | Es la arquitectura final: cuando exista catalog, solo se ajusta el contrato de sus eventos. |
| Captura (§3.11/flujo) | Capturar al recibir `PaymentAuthorized` | Pagado al autorizar; captura manual posterior | Equivale a cobrar en el acto. Si no se captura, la retención caduca y no se cobra nada. |
| Canal del correo (§3.1) | Evento `NotificationRequested` por **outbox** | `best-effort`; HTTP `requestNotification` | El correo no se pierde aunque caigan el broker o notifications. Se acepta que un rechazo de notifications acabe en su DLQ sin que order se entere. |
| Email del comprador | Claim `email` del token | Campo en el cuerpo | Impide encargar correos a una dirección arbitraria. El DSL no lo expresa de forma estructural, así que el generador lo implementa a mano (`CUSTOMER_EMAIL_REQUIRED`). |
| Idempotencia de `createOrder` (§3.2) | `client-key`, 24 h, **obligatoria**, una por comprador | Sin idempotencia; opcional; global | Un doble clic o un reintento crearía otro pedido y cobraría dos veces. Con una clave global, dos compradores chocarían. |
| Idempotencia de `cancelOrder` y de los eventos (§3.2) | Por estado; `metadata.eventId` más la guarda del lifecycle | `client-key` | La transición a `canceling` no se puede repetir, y payment también es idempotente por estado. |
| Caché (§3.3) | Ninguna | Caché corta | El comprador consulta el estado mientras espera el pago, y la respuesta depende de quién pregunta. |
| Concurrencia (§3.9) | Bloqueo optimista (`all`) | Último gana | Compiten los eventos, el barrido y `cancelOrder`. Con último-gana, una anulación podría pisar una captura. |
| Política de fallo de las suscripciones (§3.5) | 5 intentos exponenciales + DLQ | Reintentos sin DLQ; DLQ sin reintentos | Los fallos pasajeros y los 409 se resuelven solos, y un mensaje envenenado queda revisable. |
| Resiliencia hacia payment (§3.6) | 5 s + 3 intentos + circuit breaker | Sin reintentos | Reintentar es seguro: payment deduplica por `chargeRequestId` y por estado. |
| payment caído en `createOrder` (§3.6) | `201` en `pendingPayment` | `503 PAYMENT_UNAVAILABLE` | Un timeout no distingue «no llegó» de «se cobró». `pendingPayment` es honesto y distinguible. |
| Superficie M2M (§3.4) | Solo compradores | Operación M2M; `audience: both` | Hoy no hay consumidores. Si aparece uno, tendrá operación propia. |
| Paginación (§3.8) | 20 / 100, `createdAt` desc, solo `sortable: [createdAt]` | Sin paginar | Es la pantalla de «mis pedidos» y crece sin cota. |
| Frontera transaccional (§3.7) | `per-aggregate` | `per-operation` | Nada escribe `Order` y `Product` a la vez, así que no queda ninguna mitad que pueda confirmarse sola. |
| Auditoría (§3.9b) | Tiempos `declared`, autoría `none` | Autoría `all` | El comprador ya es `customerRef`, y la mayoría de las escrituras vienen de eventos, sin usuario. |
| Pedido ajeno (`readMiss`) | `404` | `403` | No revela que el id existe. `cancelOrder` responde `403` porque el DSL lo exige en las escrituras; se acepta esa asimetría. |
| Réplica: borrado | Lápida (`retired`) | Borrado físico | Un alta vieja que llega después de una retirada no debe resucitar el producto. |
| Réplica: precio ≤ 0 | El producto deja de poder pedirse | DLQ con el precio anterior | Es mejor no vender que vender a un precio que catalog ya no sostiene. |
| Réplica: guarda contra reentregas | Riesgo **aceptado** | Guarda por clave natural | Lo eligió el diseñador **frente a la recomendación**: catalog es emulado. Un fallo pasajero tras el reclamo pierde ese cambio hasta el siguiente evento. Hay que revisarlo con el catalog real. |
| Datos personales | Se conservan **sin plazo** | Anonimizar a los 5 años | Lo eligió el diseñador **frente a la recomendación**, hasta fijar la política legal. Será una evolución minor. |
| `paymentId` en todos los desenlaces | Se guarda siempre que payment lo informa | Ambigüedad aceptada | Así `null` significa solo «payment no lo registró» (`notReceived`). |
| Marcas de espera | Internas, excluidas de las respuestas | Exponerlas | Son marcadores operativos. Su efecto se prueba con los escenarios del barrido. |
| Longitud del `sub` | Se acepta que el IdP emite `sub` de 200 caracteres o menos | Error propio | Los habituales son UUID, y payment también acota a 200. |

## 7. Ficha de reutilización

### Contrato estable vs adaptable

- **Estable** (romperlo es un cambio major):
  - los códigos de error: `CUSTOMER_EMAIL_REQUIRED`, `IDEMPOTENCY_KEY_*`, `PAYMENT_SOURCE_INVALID`,
    `DUPLICATE_ORDER_LINE`, `PRODUCT_NOT_AVAILABLE`, `CURRENCY_MISMATCH`, `ORDER_NOT_FOUND`,
    `ORDER_NOT_OWNED`, `ORDER_NOT_CANCELABLE`, `ORDER_PAYMENT_ADVANCED`, `ORDER_CANCEL_REJECTED`,
    `CONCURRENT_MODIFICATION`;
  - los 4 endpoints y la proyección de `Order`;
  - los valores de `OrderStatus`;
  - la firma de `NotificationRequested` (la fija notifications), con las plantillas y sus variables;
  - el formato `order:<id>` de `chargeRequestId`;
  - el contrato **propuesto** de `ProductUpserted` y `ProductRetired`.
- **Adaptable** sin romper a nadie:
  - los plazos de los barridos (`paymentPendingTimeoutMinutes`, `paymentActionRetryMinutes`,
    `actionRequiredTimeoutHours`);
  - la resiliencia del cliente `payment`;
  - el tamaño de página;
  - el lote del barrido.
- **Versionado del spec** (`docs/methodology.md`): `patch` para cambios sin efecto observable,
  `minor` para lo que solo añade, `major` para lo que rompe.

### Puntos de extensión típicos

- **Devoluciones**: un estado `refunded` tras `paid`, una operación `refundOrder` y la activación
  `refundPayment` con `payment:refund`.
- **Stock**: una activación hacia un servicio de inventario, con compensación si el cobro falla.
- **Retención de datos personales**: `retention: anonymize` sobre `Order`.
- **Consumidores M2M**: operaciones propias con `audience: services`.
- **El catalog real**: sustituir el contrato propuesto por su `INTEGRATION.md` y fijar
  `contract.version`.
- **Reutilizable en otro servicio**: el patrón «llamada fuera de la transacción, desenlace diferido
  por eventos y barrido con marca de espera propia» es el mismo para cualquier encargo a payment.

### Supuestos y limitaciones

- **Supuestos**:
  - un solo tenant (una tienda), sin aislamiento por organización;
  - volumen moderado: el barrido de 100 pedidos cada 5 minutos basta;
  - una moneda por pedido, sin conversión de divisas;
  - el proveedor de identidad emite `sub` de 200 caracteres o menos e incluye el claim `email`.
- **Fuera de alcance a propósito**:
  - devoluciones y reembolsos (`paid` es terminal);
  - inventario y stock (no se reserva nada);
  - envío, direcciones, descuentos e impuestos (el importe es la suma de las líneas);
  - backoffice y consulta por otros servidores.
- **Limitaciones conocidas**:
  - catalog está **emulado**: la réplica arranca vacía y la carga inicial la hace el emisor de
    pruebas, sin resincronización;
  - la rama del barrido que rescata `actionRequired` no tiene escenario de caja negra y se verifica
    de forma estática.

### Cómo reutilizarlo

`keel describe order` da el resumen mecánico.

- **Adoptarlo tal cual**: copia `specs/order/` y `docs/order/` (o `keel registry get` si se publica)
  y pasa directo a generar.
- **Adaptarlo**: `keel new <nuevo> --from order`.

Lo esperable es **derivarlo**. El contrato de catalog es una propuesta y casi todo contexto real
tendrá que cambiar al menos la dependencia de catalog, las plantillas de correo o el alcance
(devoluciones, stock).

## Cobertura de comportamiento

`specs/order/validation-scenarios.md` cubre las 13 operaciones y todos los códigos de error en 38
flujos:

- **pedidos del comprador**: alta, 3DS, fallos, idempotencia con reintento y carrera, precedencias,
  propiedad y paginación;
- **los 6 eventos de payment**, con su reentrega;
- **anulación**;
- **reconciliación**: las tres esperas agotadas, el rescate y el barrido en clúster;
- **réplica**: lápida, versión y arranque en frío;
- **outbox**: canal indisponible y evento abandonado;
- **seguridad y CORS**.
