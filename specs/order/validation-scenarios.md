# order — Escenarios de validación

> Escenarios de aceptación ejecutables (Given/When/Then) derivados de
> specs/order v0.1.0. Contrato de validación para la fase de generación.

## Convenciones de determinación

- **Ausencia vs nulo** (`conventions.nulls: include` en el manifiesto): un campo sin valor viaja como `null`, en respuestas y en payloads de evento. Una colección vacía viaja como `[]`.
- **Importes**: número JSON en unidades mayores de la moneda (`32.25`), comparado **por valor** (`32.25` = `32.250`). `totalAmount` es la suma exacta de `unitPrice × quantity` de las líneas; con los precios de estos escenarios no hay redondeo. Un importe de entrada con más de 3 decimales se rechaza con `400` (`scalePolicy: reject`), nunca se redondea.
- **Instantes**: ISO-8601 en UTC con milisegundos y sufijo `Z` (`2026-11-19T10:30:00.000Z`). Los instantes que pone el servidor (`createdAt`, `updatedAt`) se verifican **por forma y por rango** (entre el inicio y el fin del escenario), jamás por valor.
- **Identificadores generados** (`id` del pedido y de sus líneas): se verifican por forma UUID y por reutilización simbólica (`{o1}`, `{o1.lines[0].id}`).
- **Cuerpo de error**: siempre `{timestamp, status, error, code, message, details, correlationId}`. Los escenarios solo afirman `status` y `code`.
- **Comparación de texto**: `exact` en todo el servicio (no hay campos `unique` de texto ni filtros de texto).
- **Rutas**: `basePath` `/api/v1`. Todas las llamadas HTTP de comprador llevan `Authorization: Bearer <token>`.
- **Compradores de prueba**: «el token de `ana`» es un token de usuario válido con `sub: "ana"` y `email: "ana@example.com"`; «el token de `luis`», con `sub: "luis"` y `email: "luis@example.com"`. Cualquier usuario autenticado es comprador (no hay roles).
- **Productos de prueba** (se crean en cada flujo publicando `ProductUpserted` en `catalogEvents`, con envoltura Keel y `source: catalog`):

  | símbolo | productId | sku | name | price | currency | version |
  |---|---|---|---|---|---|---|
  | `P1` | `aaaaaaaa-0000-4000-8000-000000000001` | `SKU-001` | `Camiseta` | `12.50` | `EUR` | `1` |
  | `P2` | `aaaaaaaa-0000-4000-8000-000000000002` | `SKU-002` | `Gorra` | `7.25` | `EUR` | `1` |
  | `P3` | `aaaaaaaa-0000-4000-8000-000000000003` | `SKU-003` | `Libro` | `20.00` | `USD` | `1` |
  | `P9` | `aaaaaaaa-0000-4000-8000-000000000009` | — | — | — | — | nunca publicado |

  «El catálogo base» = `P1`, `P2` y `P3` publicados con `version: 1`, y aplicados (el producto es pedible).
- **payment** es un doble de pruebas del contrato `docs/payment/INTEGRATION.md` 0.1.4. Los escenarios fijan lo que **responde** a cada llamada y afirman lo que **recibe**. Cuando un escenario abrevia una respuesta del doble (`{ status: "authorized" }`), esta lleva además todos los campos que `http-clients` declara `required`: `id` = el cobro del pedido (`PAY1` salvo que el escenario fije otro, y distinto por pedido cuando hay varios) y, en `requestCharge`, `chargeRequestId: "order:<id del pedido>"`; los opcionales que no se nombran van a `null`. Sus eventos se publican en `paymentEvents` con envoltura Keel y `source: payment`. `PAY1` = `bbbbbbbb-0000-4000-8000-000000000001`.
- **Proyección de `Order`** (salida de `createOrder`, `getOrder`, `cancelOrder` y de cada elemento de `listMyOrders`). El cuerpo lleva **exactamente** estos campos y ningún otro: `id`, `customerRef`, `customerEmail`, `status`, `currency`, `totalAmount`, `lineCount`, `paymentId`, `customerAction`, `failureReason`, `createdAt`, `updatedAt` y `lines`. Cada línea lleva **exactamente** `id`, `orderId`, `productId`, `sku`, `productName`, `unitPrice` y `quantity`. **Nunca** viajan `chargeOrderAwaitingSince`, `captureOrderPaymentAwaitingSince`, `actionRequiredSince` ni `cancelOrderPaymentAwaitingSince` (excluidos), ni un campo de versión.
- **Orden de `lines`**: indiferente. Se compara como conjunto, por `productId`.
- **Correo encargado**: «se encarga el correo `<plantilla>`» significa que el canal `notificationRequests` recibe **exactamente un** `NotificationRequested` (envoltura Keel, `metadata.source: "order"`) con payload `{ idempotencyKey: "order:<id>:<desenlace>", templateCode: "<plantilla>", recipients: ["<customerEmail>"], variables: [...] }`. `variables` lleva `orderId` (el id del pedido), `totalAmount` (texto decimal con los decimales de la moneda: `"32.25"`) y `currency`, y en `order-payment-failed` además `failureReason` (el código). El orden de `variables` es indiferente.

## Matriz de cobertura

| Operación | Flujos | Superficie |
|-----------|--------|------------|
| createOrder | FL-ORD-001, FL-ORD-002, FL-ORD-003, FL-ORD-004, FL-ORD-005, FL-ORD-006, FL-ORD-007, FL-ORD-008, FL-ORD-009, FL-ORD-010, FL-ORD-011, FL-OBX-001, FL-OBX-002, FL-SEC-001 | usuarios |
| getOrder | FL-ORD-001, FL-ORD-012, FL-SEC-001 | usuarios |
| listMyOrders | FL-ORD-013, FL-ORD-012, FL-SEC-001 | usuarios |
| cancelOrder | FL-CAN-001, FL-CAN-002, FL-CAN-003, FL-CAN-004, FL-CAN-005, FL-CAN-006, FL-SEC-001 | usuarios |
| applyPaymentAuthorized | FL-PAY-001, FL-PAY-007, FL-CAN-004 | evento `PaymentAuthorized` |
| applyPaymentActionRequired | FL-PAY-002 | evento `PaymentActionRequired` |
| applyPaymentCaptured | FL-PAY-001 | evento `PaymentCaptured` |
| applyPaymentFailed | FL-PAY-003 | evento `PaymentFailed` |
| applyPaymentCanceled | FL-PAY-004 | evento `PaymentCanceled` |
| applyPaymentActionRejected | FL-PAY-005 | evento `PaymentActionRejected` |
| reconcileStalledOrders | FL-REC-001, FL-REC-002, FL-REC-003, FL-REC-004, FL-REC-005, FL-REC-007 | schedule |
| upsertProduct | FL-PRD-001, FL-PRD-002, FL-PRD-003 | evento `ProductUpserted` |
| removeProduct | FL-PRD-002, FL-PRD-004 | evento `ProductRetired` |

## Pedidos del comprador

### FL-ORD-001: alta de un pedido autorizado al momento

**Given**: el catálogo base. payment responde a `requestCharge` con `201` y el cobro `{ id: PAY1, chargeRequestId: "order:<id>", status: "authorized", failureReason: null, customerAction: null }`. El canal `notificationRequests` está vacío.

**When**: `createOrder` — `POST /api/v1/orders` con el token de `ana` y la cabecera `Idempotency-Key: k-ana-001`
```json
{ "lines": [ { "productId": "aaaaaaaa-0000-4000-8000-000000000001", "quantity": 2 },
             { "productId": "aaaaaaaa-0000-4000-8000-000000000002", "quantity": 1 } ],
  "paymentToken": "tok_visa_ok" }
```

**Then**:
1. Status `201`.
2. La cabecera `Location` termina en `/api/v1/orders/{o1}`, donde `{o1}` es el `id` del cuerpo.
3. El cuerpo es la proyección de `Order` con `id: {o1}` (UUID), `customerRef: "ana"`, `customerEmail: "ana@example.com"`, `status: "authorized"`, `currency: "EUR"`, `totalAmount: 32.25`, `lineCount: 2`, `paymentId: PAY1`, `customerAction: null`, `failureReason: null`, y `createdAt`/`updatedAt` por forma.
4. `lines` tiene dos elementos: `{ productId: P1, sku: "SKU-001", productName: "Camiseta", unitPrice: 12.50, quantity: 2, orderId: {o1} }` y `{ productId: P2, sku: "SKU-002", productName: "Gorra", unitPrice: 7.25, quantity: 1, orderId: {o1} }`, cada uno con su `id` UUID.
5. El cuerpo **no** trae `chargeOrderAwaitingSince`, `captureOrderPaymentAwaitingSince`, `actionRequiredSince` ni `cancelOrderPaymentAwaitingSince`.
6. payment recibió **exactamente una** llamada `POST /payments` con el cuerpo `{ chargeRequestId: "order:{o1}", customerRef: "ana", amount: 32.25, currency: "EUR", paymentToken: "tok_visa_ok" }`, y `paymentMethodId` ausente o `null` (indiferente).
7. payment **no** recibió ninguna llamada de captura (la pide `applyPaymentAuthorized`, no esta operación).
8. `getOrder` — `GET /api/v1/orders/{o1}` con el token de `ana` responde `200` con el mismo cuerpo del punto 3.
9. El canal `notificationRequests` no recibió nada.

**Orden de evaluación**:
1. Token válido → `401` (framework).
2. Forma del cuerpo (`lines` de 1 a 50, `quantity` de 1 a 99, tipos) → `400` (framework, validación en el borde).
3. Claim `email` presente y válido → `CUSTOMER_EMAIL_REQUIRED` (`422`).
4. Cabecera `Idempotency-Key` presente → `IDEMPOTENCY_KEY_REQUIRED` (`400`).
5. Repetición de la clave → respuesta reproducida, `IDEMPOTENCY_KEY_REUSED` (`409`) o `IDEMPOTENCY_KEY_IN_PROGRESS` (`409`).
6. Exactamente una fuente de pago → `PAYMENT_SOURCE_INVALID` (`400`).
7. Sin `productId` repetido → `DUPLICATE_ORDER_LINE` (`422`).
8. Todos los productos en la copia y no retirados → `PRODUCT_NOT_AVAILABLE` (`422`).
9. Una sola moneda → `CURRENCY_MISMATCH` (`422`).

**Casos borde**:
- `lines: []` → `400`.
- 51 líneas → `400` (`maxItems: 50`).
- `quantity: 0` o `quantity: 100` → `400`.
- Sin cuerpo → `400`.

**Notas de determinación**: `totalAmount = 2 × 12.50 + 1 × 7.25 = 32.25`, comparado por valor.

### FL-ORD-002: el cobro necesita 3DS

**Given**: el catálogo base. payment responde a `requestCharge` con el cobro `{ id: PAY1, status: "actionRequired", customerAction: { "type": "redirect", "url": "https://3ds.example/c/1" }, failureReason: null }`.

**When**: `createOrder` — `POST /api/v1/orders` con el token de `ana`, `Idempotency-Key: k-ana-002`
```json
{ "lines": [ { "productId": "aaaaaaaa-0000-4000-8000-000000000001", "quantity": 1 } ],
  "paymentMethodId": "cccccccc-0000-4000-8000-000000000001" }
```

**Then**:
1. Status `201` con `Location` hacia `/api/v1/orders/{o1}`.
2. El cuerpo es la proyección de `Order` con `status: "actionRequired"`, `totalAmount: 12.50`, `lineCount: 1`, `paymentId: PAY1`, `customerAction: { "type": "redirect", "url": "https://3ds.example/c/1" }` (un **objeto JSON**, no una cadena) y `failureReason: null`.
3. payment recibió `POST /payments` con `paymentMethodId: "cccccccc-0000-4000-8000-000000000001"` y `paymentToken` ausente o `null` (indiferente).
4. El canal `notificationRequests` no recibió nada.

### FL-ORD-003: el cobro falla al momento y se encarga el correo de fallo

**Given**: el catálogo base. payment responde a `requestCharge` con el cobro `{ id: PAY1, status: "failed", failureReason: "insufficientFunds", customerAction: null }`.

**When**: `createOrder` — `POST /api/v1/orders` con el token de `ana`, `Idempotency-Key: k-ana-003`, una línea `P1 × 2` y `paymentToken: "tok_no_funds"`.

**Then**:
1. Status `201`: un rechazo del cobro **no** es un error HTTP.
2. El cuerpo es la proyección de `Order` con `status: "paymentFailed"`, `failureReason: "insufficientFunds"`, `totalAmount: 25.00`, `paymentId: PAY1` y `customerAction: null`.
3. Se encarga el correo `order-payment-failed` con `idempotencyKey: "order:{o1}:payment-failed"`, `recipients: ["ana@example.com"]` y las variables `orderId: {o1}`, `totalAmount: "25.00"`, `currency: "EUR"` y `failureReason: "insufficientFunds"`.

### FL-ORD-004: payment rechaza el medio, o rechaza la petición por otro motivo

**Given**: el catálogo base. payment responde a la primera llamada `requestCharge` con `422 PAYMENT_METHOD_UNAVAILABLE`, y a la segunda con `400 CURRENCY_UNKNOWN`.

**When**: `createOrder` con el token de `ana`, `Idempotency-Key: k-ana-004a`, una línea `P1 × 1` y `paymentMethodId: "cccccccc-0000-4000-8000-000000000009"`.

**Then**:
1. Status `201`. El cuerpo es la proyección de `Order` con `status: "paymentFailed"`, `failureReason: "invalidPaymentMethod"`, `paymentId: null` y `customerAction: null`.
2. Se encarga el correo `order-payment-failed` con `failureReason: "invalidPaymentMethod"`.

**When**: `createOrder` con el token de `ana`, `Idempotency-Key: k-ana-004b`, una línea `P2 × 1` y `paymentToken: "tok_x"`.

**Then**:
3. Status `201`. El cuerpo es la proyección de `Order` con `status: "paymentFailed"`, `failureReason: "processingError"` y `paymentId: null`.
4. Se encarga el correo `order-payment-failed` con `failureReason: "processingError"`.

### FL-ORD-005: payment no contesta — el pedido queda pendiente

**Given**: el catálogo base. payment no contesta a `requestCharge` dentro del timeout, en ninguno de sus intentos.

**When**: `createOrder` con el token de `ana`, `Idempotency-Key: k-ana-005`, una línea `P1 × 1` y `paymentToken: "tok_visa_ok"`.

**Then**:
1. Status `201` con `Location`.
2. El cuerpo es la proyección de `Order` con `status: "pendingPayment"`, `paymentId: null`, `customerAction: null` y `failureReason: null`.
3. payment recibió **3** llamadas `POST /payments`, todas con el mismo cuerpo (`chargeRequestId: "order:{o1}"`): son los reintentos de la misma petición.
4. El canal `notificationRequests` no recibió nada.

**Ramas condicionales**: si payment responde `401` o `403` a la llamada (credencial M2M rechazada), o no se puede obtener el token M2M, el resultado es el mismo: `201` con el pedido en `pendingPayment`.

### FL-ORD-006: payment devuelve un cobro ya capturado o anulado (respuesta a una repetición)

**Given**: el catálogo base. payment responde a la primera llamada `requestCharge` con `{ id: PAY1, status: "captured" }`, y a la segunda con `{ id: "bbbbbbbb-0000-4000-8000-000000000002", status: "canceled" }`.

**When**: `createOrder` con el token de `ana`, `Idempotency-Key: k-ana-006a`, una línea `P1 × 1` y un `paymentToken`.

**Then**:
1. Status `201` con el pedido en `status: "authorized"` y `paymentId: PAY1`: el evento de payment terminará el trabajo.

**When**: `createOrder` con el token de `ana`, `Idempotency-Key: k-ana-006b`, una línea `P2 × 1` y un `paymentToken`.

**Then**:
2. Status `201` con el pedido en `status: "canceled"`.
3. Se encarga el correo `order-canceled` con `idempotencyKey: "order:{o2}:canceled"`.

### FL-ORD-007: reintento con la misma clave y clave reutilizada con otro contenido

**Given**: el catálogo base. payment responde a `requestCharge` con `{ id: PAY1, status: "authorized" }`.

**When**: `createOrder` con el token de `ana`, `Idempotency-Key: k-ana-007`, una línea `P1 × 2` y `paymentToken: "tok_visa_ok"`.

**Then**:
1. Status `201` con el pedido `{o1}` en `authorized`.

**When**: se repite la **misma** petición con la misma clave `k-ana-007`.

**Then**:
2. Status `201` con el mismo `id: {o1}` y el pedido en su estado **actual** (`authorized`), con el mismo cuerpo que el punto 1.
3. La cabecera `Location` vuelve, hacia `/api/v1/orders/{o1}`.
4. Hasta este punto del flujo, payment recibió **una sola** llamada `POST /payments`.
5. `listMyOrders` con el token de `ana` devuelve `totalElements: 1`.

**When**: se envía con la clave `k-ana-007` una petición con **otro** contenido (una línea `P2 × 1`).

**Then**:
6. Status `409` con code `IDEMPOTENCY_KEY_REUSED`.

**When**: `luis` envía con su token la petición del punto 1 con la **misma** clave `k-ana-007`.

**Then**:
7. Status `201` con un pedido **nuevo** de `luis` (`id` distinto de `{o1}`, `customerRef: "luis"`): la clave es por comprador (`partitionBy: [customerRef]`).

**When**: el mismo contenido con una clave **distinta**, `k-ana-007b`, y el token de `ana`.

**Then**:
8. Status `201` con un pedido nuevo de `ana`; `listMyOrders` de `ana` devuelve `totalElements: 2`.

### FL-ORD-008: dos peticiones con la misma clave a la vez

**Given**: el catálogo base. payment responde a `requestCharge` con `{ id: PAY1, status: "authorized" }` con 500 ms de retardo.

**When**: `createOrder` se envía **dos veces a la vez** con el token de `ana`, la misma `Idempotency-Key: k-ana-008` y el mismo cuerpo (una línea `P1 × 1` y un `paymentToken`).

**Then**:
1. Una de dos, y nada más: las dos responden `201` con el mismo `id`, o una responde `201` y la otra `409` con code `IDEMPOTENCY_KEY_IN_PROGRESS`.
2. `listMyOrders` con el token de `ana` devuelve `totalElements: 1`.
3. payment recibió **exactamente una** llamada `POST /payments`.

### FL-ORD-009: errores de validación de negocio y su precedencia

**Given**: el catálogo base. `P1` y `P2` son `EUR`; `P3` es `USD`; `P9` nunca se publicó.

**When**: `createOrder` con el token de `ana`, `Idempotency-Key: k-ana-009a`, una línea `P1 × 1` y **sin** fuente de pago.

**Then**:
1. Status `400` con code `PAYMENT_SOURCE_INVALID`.

**When**: lo mismo con `paymentToken` **y** `paymentMethodId` a la vez (`k-ana-009b`).

**Then**:
2. Status `400` con code `PAYMENT_SOURCE_INVALID`.

**When**: líneas `P1 × 1` y `P1 × 2`, con `paymentToken` (`k-ana-009c`).

**Then**:
3. Status `422` con code `DUPLICATE_ORDER_LINE`.

**When**: líneas `P1 × 1` y `P9 × 1`, con `paymentToken` (`k-ana-009d`).

**Then**:
4. Status `422` con code `PRODUCT_NOT_AVAILABLE`.

**When**: líneas `P1 × 1` y `P3 × 1`, con `paymentToken` (`k-ana-009e`).

**Then**:
5. Status `422` con code `CURRENCY_MISMATCH`.

**When**: **precedencia** — líneas `P9 × 1` y `P9 × 2` (repetido **e** inexistente) y sin fuente de pago (`k-ana-009f`).

**Then**:
6. Status `400` con code `PAYMENT_SOURCE_INVALID`: la guarda de la fuente de pago va antes que las de las líneas.

**When**: **precedencia** — líneas `P9 × 1` y `P3 × 1` (inexistente **y** moneda distinta), con `paymentToken` (`k-ana-009g`).

**Then**:
7. Status `422` con code `PRODUCT_NOT_AVAILABLE`: la existencia va antes que la moneda.
8. En todo el flujo payment no recibió ninguna llamada, y `listMyOrders` de `ana` devuelve `totalElements: 0`.

### FL-ORD-010: la cabecera de idempotencia es obligatoria

**Given**: el catálogo base.

**When**: `createOrder` con el token de `ana`, **sin** cabecera `Idempotency-Key`, una línea `P1 × 1` y un `paymentToken`.

**Then**:
1. Status `400` con code `IDEMPOTENCY_KEY_REQUIRED`.
2. payment no recibió ninguna llamada y `listMyOrders` de `ana` devuelve `totalElements: 0`.

**When**: **precedencia** — sin cabecera `Idempotency-Key` y con `lines: []`.

**Then**:
3. Status `400` con code `VALIDATION_ERROR` (no `IDEMPOTENCY_KEY_REQUIRED`): la forma del cuerpo se valida en el borde, antes que la cabecera de idempotencia.

### FL-ORD-011: el token del comprador no trae un email utilizable

**Given**: el catálogo base. Un token de usuario válido con `sub: "eva"` y **sin** claim `email`; otro con `sub: "eva"` y `email: "no-es-un-email"`.

**When**: `createOrder` con el primer token, `Idempotency-Key: k-eva-011a`, una línea `P1 × 1` y un `paymentToken`.

**Then**:
1. Status `422` con code `CUSTOMER_EMAIL_REQUIRED`.

**When**: lo mismo con el segundo token, `Idempotency-Key: k-eva-011b`.

**Then**:
2. Status `422` con code `CUSTOMER_EMAIL_REQUIRED`.

**When**: **precedencia** — el primer token, **sin** `Idempotency-Key` y con un cuerpo válido (una línea `P1 × 1` y un `paymentToken`).

**Then**:
3. Status `422` con code `CUSTOMER_EMAIL_REQUIRED`: el email del token se comprueba antes que la cabecera de idempotencia.

**When**: **precedencia** — el primer token, con `Idempotency-Key: k-eva-011c` y `lines: []`.

**Then**:
3b. Status `400` con code `VALIDATION_ERROR`: la forma del cuerpo se valida antes que el email del token.
4. En todo el flujo payment no recibió ninguna llamada.

### FL-ORD-012: un comprador no ve los pedidos de otro

**Given**: el catálogo base. payment responde a `requestCharge` con `{ id: PAY1, status: "authorized" }`. `ana` crea el pedido `{o1}` (`createOrder`, `Idempotency-Key: k-ana-012`, `P1 × 1`).

**When**: `getOrder` — `GET /api/v1/orders/{o1}` con el token de `luis`.

**Then**:
1. Status `404` con code `ORDER_NOT_FOUND`: el pedido ajeno se ve como inexistente (`readMiss: not-found`).

**When**: `listMyOrders` — `GET /api/v1/orders` con el token de `luis`.

**Then**:
2. Status `200` con `{ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }`: el pedido de `ana` no aparece y no cuenta en el total.

**When**: `getOrder` — `GET /api/v1/orders/dddddddd-0000-4000-8000-000000000000` (inexistente) con el token de `ana`.

**Then**:
3. Status `404` con code `ORDER_NOT_FOUND`.

### FL-ORD-013: listado paginado de mis pedidos

**Given**: el catálogo base. payment responde a todo `requestCharge` con un cobro `authorized`. `ana` crea, **uno detrás de otro**, 25 pedidos `{a1}`…`{a25}` (cada uno con su clave y una línea `P1 × 1`), y después `luis` crea 1 pedido.

**When**: `listMyOrders` — `GET /api/v1/orders` con el token de `ana`.

**Then**:
1. Status `200` con `page: 0`, `size: 20`, `totalElements: 25`, `totalPages: 2` y 20 `items`.
2. Los `items` van ordenados por `createdAt` descendente, con el `id` como desempate: el primero es `{a25}` y el último `{a6}`. Cada uno es la proyección de `Order`, con `customerRef: "ana"`.
3. Ningún elemento es de `luis`.

**When**: `GET /api/v1/orders?page=1&size=20` con el token de `ana`.

**Then**:
4. Status `200` con `page: 1`, 5 `items` (`{a5}` … `{a1}`, en ese orden), `totalElements: 25` y `totalPages: 2`.

**When**: `GET /api/v1/orders?page=5&size=20` con el token de `ana`.

**Then**:
5. Status `200` con `{ items: [], page: 5, size: 20, totalElements: 0, totalPages: 0 }` (sobre canónico de página fuera de rango).

**When**: `GET /api/v1/orders?size=500` con el token de `ana`.

**Then**:
6. Status `200` con `size: 100` (tope `maxSize`) y los 25 `items`.

**When**: `GET /api/v1/orders?sort=createdAt,asc` con el token de `ana`.

**Then**:
7. Status `200`; el primer elemento es `{a1}` y el vigésimo `{a20}`.

**When**: `GET /api/v1/orders?sort=totalAmount,desc` con el token de `ana`.

**Then**:
8. Status `400`: `totalAmount` no está en `sortable`.

**When**: `GET /api/v1/orders?status=paid` con el token de `ana`.

**Then**:
9. Status `200` con `items: []` y `totalElements: 0` (todos los pedidos están en `authorized`).

**Casos borde**:
- `status=desconocido` → `400`.

**Notas de determinación**: se ordena por `createdAt`, que no cambia tras la creación, y los pedidos se crean en secuencia.

## Desenlaces del cobro (eventos de payment)

### FL-PAY-001: autorizado → captura → pagado, con su correo

**Given**: el catálogo base. payment responde a `requestCharge` con `{ id: PAY1, status: "pending" }` y a `capturePayment` con `{ id: PAY1, status: "captured" }`. `ana` crea el pedido `{o1}` (`P1 × 2`, `Idempotency-Key: k-ana-p01`), que queda en `pendingPayment`.

**When**: llega `PaymentAuthorized` por `paymentEvents` (`source: payment`, `metadata.eventId: e-auth-1`) con `{ paymentId: PAY1, chargeRequestId: "order:{o1}", customerRef: "ana", amount: 25.00, currency: "EUR", gatewayPaymentId: "gw_1" }`.

**Then**:
1. `getOrder` de `{o1}` con el token de `ana` devuelve `status: "authorized"`, `paymentId: PAY1` y `customerAction: null`.
2. payment recibió **exactamente una** llamada `POST /payments/PAY1/capture`.

**When**: se **reentrega** el mismo `PaymentAuthorized` (mismo `metadata.eventId: e-auth-1`).

**Then**:
3. payment sigue habiendo recibido **una sola** llamada de captura, y el pedido sigue en `authorized`.

**When**: llega `PaymentCaptured` (`metadata.eventId: e-cap-1`) con `{ paymentId: PAY1, chargeRequestId: "order:{o1}", customerRef: "ana", amount: 25.00, currency: "EUR" }`.

**Then**:
4. `getOrder` de `{o1}` devuelve `status: "paid"`, `paymentId: PAY1`, `failureReason: null` y `customerAction: null`.
5. Se encarga el correo `order-paid` con `idempotencyKey: "order:{o1}:paid"`, `recipients: ["ana@example.com"]` y las variables `orderId: {o1}`, `totalAmount: "25.00"` y `currency: "EUR"` (sin `failureReason`).

**When**: se **reentrega** el mismo `PaymentCaptured` (`e-cap-1`).

**Then**:
6. El pedido sigue en `paid` y el canal `notificationRequests` sigue con **un solo** `NotificationRequested` de `{o1}`.

**When**: llega `PaymentAuthorized` con `chargeRequestId: "checkout:xyz"` (un cobro de otro cliente de payment).

**Then**:
7. Se ignora sin error: el mensaje no va a la deadLetter y no se pide ninguna captura más.

**Casos borde**:
- **PaymentAuthorized perdido**: si `createOrder` deja el pedido en `authorized` por la respuesta síncrona y el `PaymentAuthorized` acaba en la deadLetter, el barrido pide la captura igualmente cuando vence la marca de captura que estampó `createOrder` (ver FL-REC-004).
- `PaymentCaptured` para un pedido en `pendingPayment` (sin autorizar antes) → no hace nada: el pedido sigue en `pendingPayment` y no se encarga ningún correo.
- Si el procesamiento de un evento de este flujo falla de forma pasajera (el almacén no responde), el mensaje se procesa hasta 5 veces **en total** (`maxAttempts: 5`) con espera exponencial. Si sigue fallando, va a la deadLetter de la suscripción y el pedido no cambia.

### FL-PAY-002: el cobro pasa a necesitar 3DS por evento

**Given**: el catálogo base. payment responde a `requestCharge` con `{ id: PAY1, status: "pending" }`. `ana` crea `{o1}` (`P2 × 1`, `k-ana-p02`) en `pendingPayment`.

**When**: llega `PaymentActionRequired` (`e-ar-1`) con `{ paymentId: PAY1, chargeRequestId: "order:{o1}", customerRef: "ana", amount: 7.25, currency: "EUR", customerAction: { "type": "redirect", "url": "https://3ds.example/c/2" } }`.

**Then**:
1. `getOrder` de `{o1}` devuelve `status: "actionRequired"`, `paymentId: PAY1` y `customerAction: { "type": "redirect", "url": "https://3ds.example/c/2" }`.

**When**: se **reentrega** el mismo `PaymentActionRequired` (`e-ar-1`).

**Then**:
2. El pedido no cambia.

### FL-PAY-003: el cobro falla por evento

**Given**: el catálogo base. payment responde a `requestCharge` con `{ id: PAY1, status: "actionRequired", customerAction: { "type": "redirect", "url": "https://3ds.example/c/3" } }`. `ana` crea `{o1}` (`P1 × 1`, `k-ana-p03`) en `actionRequired`.

**When**: llega `PaymentFailed` (`e-fail-1`) con `{ paymentId: PAY1, chargeRequestId: "order:{o1}", customerRef: "ana", amount: 12.50, currency: "EUR", failureReason: "authenticationFailed" }`.

**Then**:
1. `getOrder` de `{o1}` devuelve `status: "paymentFailed"`, `failureReason: "authenticationFailed"`, `paymentId: PAY1` y `customerAction: null`.
2. Se encarga el correo `order-payment-failed` con `idempotencyKey: "order:{o1}:payment-failed"` y `failureReason: "authenticationFailed"`.

**When**: se **reentrega** el mismo `PaymentFailed` (`e-fail-1`).

**Then**:
3. El pedido no cambia y no se encarga un segundo correo.

### FL-PAY-004: la autorización queda anulada por evento

**Given**: el catálogo base. payment responde a `requestCharge` con `{ id: PAY1, status: "pending" }`. `ana` crea `{o1}` (`P1 × 1`, `k-ana-p04`) en `pendingPayment` (`paymentId: PAY1`). Después llega `PaymentActionRequired` (`e-ar-4`) con `paymentId: PAY1` y `customerAction: { "type": "redirect", "url": "https://3ds.example/c/4" }`, y el pedido queda en `actionRequired`.

**When**: llega `PaymentCanceled` (`e-can-1`) con `{ paymentId: PAY1, chargeRequestId: "order:{o1}", customerRef: "ana", amount: 12.50, currency: "EUR", requested: true }` (venció el plazo del 3DS).

**Then**:
1. `getOrder` de `{o1}` devuelve `status: "canceled"`, `paymentId: PAY1` y `customerAction: null`.
2. Se encarga el correo `order-canceled` con `idempotencyKey: "order:{o1}:canceled"`.

**When**: se **reentrega** el mismo `PaymentCanceled` (`e-can-1`).

**Then**:
3. El pedido no cambia y no se encarga un segundo correo.

**When**: llega `PaymentFailed` (`e-fail-9`) para `order:{o1}`.

**Then**:
4. No hace nada: el pedido sigue en `canceled` (un estado terminal no se abandona) y no se encarga ningún correo de fallo.

### FL-PAY-005: payment rechaza la anulación y el pedido vuelve

**Given**: el catálogo base. payment responde a `requestCharge` con `{ id: PAY1, status: "actionRequired", customerAction: { "type": "redirect", "url": "https://3ds.example/c/5" } }` y a `cancelPayment` con `{ id: PAY1, status: "canceling" }`. `ana` crea `{o1}` (`k-ana-p05`) y lo anula con `cancelOrder`, de modo que queda en `canceling`.

**When**: llega `PaymentActionRejected` (`e-rej-1`) con `{ paymentId: PAY1, chargeRequestId: "order:{o1}", customerRef: "ana", action: "cancel", refundRequestId: null, refundAmount: null, status: "actionRequired" }`.

**Then**:
1. `getOrder` de `{o1}` devuelve `status: "actionRequired"` con la misma `customerAction` de antes.

**When**: se **reentrega** el mismo `PaymentActionRejected` (`e-rej-1`).

**Then**:
2. El pedido sigue en `actionRequired`.

**When**: llega `PaymentActionRejected` (`e-rej-2`) con `action: "capture"` y `status: "authorized"` para `order:{o1}`.

**Then**:
3. No hace nada: el pedido sigue en `actionRequired`.

### FL-PAY-007: autorizado desde canceling (el 3DS se completó mientras se anulaba)

**Given**: igual que FL-PAY-005: `{o1}` en `canceling`. payment responde a `capturePayment` con `{ id: PAY1, status: "captured" }`.

**When**: llega `PaymentAuthorized` (`e-auth-7`) con `{ paymentId: PAY1, chargeRequestId: "order:{o1}", customerRef: "ana", amount: 12.50, currency: "EUR", gatewayPaymentId: "gw_7" }`.

**Then**:
1. `getOrder` de `{o1}` devuelve `status: "authorized"` y `customerAction: null`.
2. payment recibió **exactamente una** llamada `POST /payments/PAY1/capture`.

## Anulación por el comprador

### FL-CAN-001: anular un pedido que espera el 3DS

**Given**: el catálogo base. payment responde a `requestCharge` con `{ id: PAY1, status: "actionRequired", customerAction: { "type": "redirect", "url": "https://3ds.example/c/6" } }` y a `cancelPayment` con `{ id: PAY1, status: "canceled", failureReason: null }`. `ana` crea `{o1}` (`P1 × 1`, `k-ana-c01`) en `actionRequired`.

**When**: `cancelOrder` — `POST /api/v1/orders/{o1}/cancel` con el token de `ana`, sin cuerpo.

**Then**:
1. Status `200`. El cuerpo es la proyección de `Order` con `status: "canceled"`, `customerAction: null` y `failureReason: null`.
2. payment recibió **exactamente una** llamada `POST /payments/PAY1/cancel`.
3. Se encarga el correo `order-canceled` con `idempotencyKey: "order:{o1}:canceled"`.

**When**: se repite `cancelOrder` sobre `{o1}`.

**Then**:
4. Status `200` con el pedido tal cual (`canceled`). payment sigue con **una sola** llamada de anulación, y no se encarga un segundo correo.

**When**: llega `PaymentCanceled` (`e-can-c1`) para `order:{o1}`.

**Then**:
5. El pedido sigue en `canceled` y no se encarga un segundo correo.

### FL-CAN-002: no se puede anular un pedido que no espera el 3DS

**Given**: el catálogo base. payment responde a `requestCharge` con `{ id: PAY1, status: "authorized" }` y a `capturePayment` con `{ status: "captured" }`. `ana` crea `{o1}` (`k-ana-c02`), y llegan `PaymentAuthorized` y `PaymentCaptured` para `order:{o1}`: el pedido queda en `paid`.

**When**: `cancelOrder` — `POST /api/v1/orders/{o1}/cancel` con el token de `ana`.

**Then**:
1. Status `409` con code `ORDER_NOT_CANCELABLE`.
2. El pedido sigue en `paid` y payment no recibió ninguna llamada de anulación.

**When**: `cancelOrder` sobre `{o1}` con el token de `luis`.

**Then**:
3. Status `403` con code `ORDER_NOT_OWNED`.

**When**: `cancelOrder` sobre `dddddddd-0000-4000-8000-000000000000` (inexistente) con el token de `ana`.

**Then**:
4. Status `404` con code `ORDER_NOT_FOUND`.

**Orden de evaluación**:
1. Token válido → `401`.
2. El pedido existe → `ORDER_NOT_FOUND` (`404`).
3. Es del comprador → `ORDER_NOT_OWNED` (`403`).
4. Ya en `canceling` o `canceled` → se devuelve tal cual.
5. Está en `actionRequired` → `ORDER_NOT_CANCELABLE` (`409`).
6. Respuesta de payment → `ORDER_PAYMENT_ADVANCED` (`409`), `ORDER_CANCEL_REJECTED` (`422`) o el desenlace.

**Casos borde**:
- **Precedencia**: `luis` anula el pedido `paid` de `ana` → `403 ORDER_NOT_OWNED`, no `409`: la propiedad se comprueba antes que el estado.

### FL-CAN-003: payment ya no admite anular, o rechaza la anulación

**Given**: el catálogo base. payment responde a `requestCharge` con `actionRequired` (con `customerAction`) para dos pedidos de `ana`: `{o1}` (`k-ana-c03a`) con el cobro `PAY1`, y `{o2}` (`k-ana-c03b`) con el cobro `PAY2` = `bbbbbbbb-0000-4000-8000-000000000002`. payment responde a `POST /payments/PAY1/cancel` con `409 PAYMENT_NOT_CANCELABLE` y a `POST /payments/PAY2/cancel` con `422 CANCEL_REJECTED`.

**When**: `cancelOrder` sobre `{o1}` con el token de `ana`.

**Then**:
1. Status `409` con code `ORDER_PAYMENT_ADVANCED`.
2. `getOrder` de `{o1}` devuelve `status: "actionRequired"` con su `customerAction`.

**When**: `cancelOrder` sobre `{o2}` con el token de `ana`.

**Then**:
3. Status `422` con code `ORDER_CANCEL_REJECTED`.
4. `getOrder` de `{o2}` devuelve `status: "actionRequired"` con su `customerAction`.
5. En el flujo no se encargó ningún correo.

### FL-CAN-004: la anulación llega tarde — payment responde autorizado

**Given**: el catálogo base. payment responde a `requestCharge` con `actionRequired` (con `customerAction`), a `cancelPayment` con `{ id: PAY1, status: "authorized" }` y a `capturePayment` con `{ status: "captured" }`. `ana` crea `{o1}` (`k-ana-c04`) en `actionRequired`.

**When**: `cancelOrder` sobre `{o1}` con el token de `ana`.

**Then**:
1. Status `200`. El cuerpo es la proyección de `Order` con `status: "authorized"` y `customerAction: null`.

**When**: llega `PaymentAuthorized` (`e-auth-c4`) con `{ paymentId: PAY1, chargeRequestId: "order:{o1}", customerRef: "ana", amount: 12.50, currency: "EUR", gatewayPaymentId: "gw_c4" }`.

**Then**:
2. payment recibió **exactamente una** llamada de captura, y el pedido sigue en `authorized` hasta que llegue `PaymentCaptured`.

### FL-CAN-005: payment no contesta a la anulación

**Given**: el catálogo base. payment responde a `requestCharge` con `actionRequired` (con `customerAction`) y **no contesta** a `cancelPayment` en ninguno de sus intentos. `ana` crea `{o1}` (`k-ana-c05`).

**When**: `cancelOrder` sobre `{o1}` con el token de `ana`.

**Then**:
1. Status `200`. El cuerpo es la proyección de `Order` con `status: "canceling"` y su `customerAction` todavía presente.
2. No se encargó ningún correo.

**When**: llega `PaymentCanceled` (`e-can-c5`) con `{ paymentId: PAY1, chargeRequestId: "order:{o1}", customerRef: "ana", amount: 12.50, currency: "EUR", requested: true }`.

**Then**:
3. `getOrder` de `{o1}` devuelve `status: "canceled"` y se encarga el correo `order-canceled`.

**Ramas condicionales**: si payment responde `failed` a la anulación (el 3DS ya había fallado), el pedido pasa a `paymentFailed` con su `failureReason` (`processingError` si no viene) y se encarga el correo `order-payment-failed`.

### FL-CAN-006: dos anulaciones del mismo pedido a la vez

**Given**: el catálogo base. payment responde a `requestCharge` con `actionRequired` (con `customerAction`) y a `cancelPayment` con `{ status: "canceling" }`, con 300 ms de retardo. `ana` crea `{o1}` (`k-ana-c06`).

**When**: `cancelOrder` sobre `{o1}` se envía **dos veces a la vez** con el token de `ana`.

**Then**:
1. Una de dos, y nada más: las dos responden `200` con el pedido en `canceling`, o una responde `200` y la otra `409` con code `CONCURRENT_MODIFICATION`.
2. payment recibió **exactamente una** llamada `POST /payments/PAY1/cancel`.
3. `getOrder` de `{o1}` devuelve `status: "canceling"`.

## Reconciliación (barrido `reconcileStalledOrders`)

> Las marcas de espera de una fila se envejecen con el helper del arnés (`ageForReconciliation(<activación>, id)`), nunca bajando el umbral global. El cron sigue disparando solo, cada 5 minutos. Los plazos de prueba son `paymentPendingTimeoutMinutes: 1`, `paymentActionRetryMinutes: 1` y `actionRequiredTimeoutHours: 1`.
>
> **Sin escenario, por decisión del diseñador**: la rama del barrido que rescata un pedido en `actionRequired` con `actionRequiredSince` vencido. Su reloj no es la marca de ninguna activación, así que no se puede envejecer en caja negra; se verifica de forma estática. Es una red de seguridad para un `PaymentFailed` o `PaymentCanceled` perdido en la deadLetter, y el camino normal de esos eventos está cubierto en FL-PAY-003 y FL-PAY-004.

### FL-REC-001: el cobro que payment nunca registró se da por fallido (espera agotada de chargeOrder)

**Given**: el catálogo base. payment no contesta a `requestCharge`, así que `ana` crea `{o1}` (`k-ana-r01`) en `pendingPayment`. payment responde a `GET /payments/by-charge-request/order:{o1}` con `404 PAYMENT_NOT_FOUND`. La marca de espera de `chargeOrder` de `{o1}` lleva más de `unansweredAfterSeconds` (envejecida). El canal `notificationRequests` está purgado.

**When**: pasa un ciclo de `reconcileStalledOrders`.

**Then**:
1. `getOrder` de `{o1}` devuelve `status: "paymentFailed"`, `failureReason: "notReceived"` y `paymentId: null` (payment nunca registró el cobro).
2. payment recibió **una** consulta `GET /payments/by-charge-request/order:{o1}`.
3. Se encarga **exactamente un** correo `order-payment-failed` con `failureReason: "notReceived"`.

**When**: pasa otro ciclo del barrido.

**Then**:
4. No hay una segunda consulta para `{o1}`, ni un segundo correo.

### FL-REC-002: rescate de un pedido pendiente que payment sí cobró, y lo que acaba de entrar en espera no se toca

**Given**: el catálogo base. payment no contesta a `requestCharge`. `ana` crea `{o1}` (`k-ana-r02a`) y `{o2}` (`k-ana-r02b`), los dos en `pendingPayment`. La marca de `{o1}` está envejecida; la de `{o2}` está retenida en el futuro con `holdFromReconciliation(chargeOrder, {o2})`. payment responde a la consulta de `order:{o1}` con `{ id: PAY1, status: "captured" }`.

**When**: pasa un ciclo del barrido.

**Then**:
1. `getOrder` de `{o1}` devuelve `status: "paid"` y `paymentId: PAY1` (pasó por `authorized` y luego a `paid`).
2. Se encarga **exactamente un** correo `order-paid` para `{o1}`.
3. `{o2}` sigue en `pendingPayment`, y payment no recibió ninguna consulta para `order:{o2}`.

**When**: pasan dos ciclos más del barrido.

**Then**:
4. `{o2}` sigue igual y sigue sin consultarse.

### FL-REC-003: un cobro aún pendiente en payment renueva la espera

**Given**: igual que FL-REC-001, pero payment responde a la consulta de `order:{o1}` con `{ id: PAY1, status: "pending" }`.

**When**: pasa un ciclo del barrido.

**Then**:
1. `{o1}` sigue en `pendingPayment` y payment recibió **una** consulta.

**When**: pasa otro ciclo del barrido.

**Then**:
2. En ese ciclo payment recibe **como mucho una** consulta más para `order:{o1}` (nunca dos en el mismo ciclo), y el pedido sigue en `pendingPayment`.

**Notas de determinación**: con el plazo de prueba (1 minuto) por debajo del intervalo del cron (5 minutos), la marca renovada ya ha vencido en el ciclo siguiente, así que la renovación no es observable aquí. Lo que se afirma es que el pedido no se rinde a `paymentFailed` mientras payment diga `pending`.

**Ramas condicionales**: si la consulta a payment no contesta, el pedido sigue en `pendingPayment` sin cambios y se vuelve a consultar en el ciclo siguiente (`onUnavailable: degrade` de `chargeStatus`).

### FL-REC-004: la captura que no se confirmó se vuelve a pedir (espera agotada de captureOrderPayment)

**Given**: el catálogo base. payment responde a `requestCharge` con `{ id: PAY1, status: "authorized" }`. `ana` crea `{o1}` (`k-ana-r04`), que queda en `authorized` por la respuesta síncrona; su `PaymentAuthorized` **no llega** (acabó en la deadLetter), así que nadie ha pedido la captura. Después payment pasa a responder a `capturePayment` con `{ status: "captured" }`. La marca de espera de `captureOrderPayment` de `{o1}` está envejecida.

**When**: pasa un ciclo del barrido.

**Then**:
1. payment recibió una nueva llamada `POST /payments/PAY1/capture`.
2. `getOrder` de `{o1}` devuelve `status: "paid"`, y se encarga **exactamente un** correo `order-paid`.

**When**: pasa otro ciclo del barrido.

**Then**:
3. No hay más llamadas de captura para `{o1}`.

### FL-REC-005: la anulación que no se confirmó se vuelve a pedir (espera agotada de cancelOrderPayment)

**Given**: el catálogo base. payment responde a `requestCharge` con `actionRequired` (con `customerAction`) y **no contesta** a `cancelPayment`. `ana` crea `{o1}` (`k-ana-r05`) y lo anula: queda en `canceling`. Después payment pasa a responder a `cancelPayment` con `{ status: "canceled" }`. La marca de espera de `cancelOrderPayment` de `{o1}` está envejecida.

**When**: pasa un ciclo del barrido.

**Then**:
1. payment recibió una nueva llamada `POST /payments/PAY1/cancel`.
2. `getOrder` de `{o1}` devuelve `status: "canceled"`, y se encarga **exactamente un** correo `order-canceled`.

### FL-REC-007: el barrido en clúster — dos réplicas, una sola llamada por pedido

**Given**: el catálogo base. Dos instancias del servicio vivas. payment no contesta a `requestCharge`, así que `ana` crea cinco pedidos `{o1}`…`{o5}` en `pendingPayment`, todos con la marca de `chargeOrder` envejecida, y un sexto `{o6}` con la marca retenida en el futuro con `holdFromReconciliation(chargeOrder, {o6})` (fila de control). payment responde a cada consulta con `{ status: "authorized" }` y a cada captura con `{ status: "captured" }`.

**When**: pasa un ciclo de `reconcileStalledOrders` en las dos réplicas a la vez.

**Then**:
1. payment recibió **exactamente una** consulta `GET /payments/by-charge-request/order:{oN}` por cada uno de `{o1}`…`{o5}` (cinco en total), y **ninguna** para `{o6}`.
2. Los cinco pedidos acaban en `authorized` (o en `paid`), y `{o6}` sigue en `pendingPayment`.

## Réplica del catálogo

### FL-PRD-001: el alta y el cambio de precio se aplican; la reentrega no tiene efecto

**Given**: ningún producto publicado. payment responde a `requestCharge` con `authorized`.

**When**: llega `ProductUpserted` (`e-p1-v1`) por `catalogEvents` con `{ productId: P1, sku: "SKU-001", name: "Camiseta", price: 12.50, currency: "EUR", version: 1 }`, y `ana` crea un pedido `P1 × 1` (`k-ana-d01a`).

**Then**:
1. El pedido sale con `unitPrice: 12.50` y `totalAmount: 12.50`.

**When**: llega `ProductUpserted` (`e-p1-v2`) con `price: 15.00` y `version: 2`, y `ana` crea otro pedido `P1 × 1` (`k-ana-d01b`).

**Then**:
2. El nuevo pedido sale con `unitPrice: 15.00`, y el primer pedido, leído con `getOrder`, sigue con `unitPrice: 12.50` (precio congelado).

**When**: se **reentrega** `ProductUpserted` `e-p1-v1` (`price: 12.50`, `version: 1`), y `ana` crea un pedido `P1 × 1` (`k-ana-d01c`).

**Then**:
3. El pedido sale con `unitPrice: 15.00`: la versión vieja se descarta.

**When**: llega `ProductUpserted` (`e-p1-bad`) con `price: 0` y `version: 3`.

**Then**:
4. El mensaje se procesa sin ir a la deadLetter, y desde entonces un pedido de `P1` (`k-ana-d01d`) responde `422` con code `PRODUCT_NOT_AVAILABLE`: un precio que catalog ya no sostiene deja el producto sin poder pedirse.

**Notas de determinación**: el producto no se lee por la API (la réplica no se expone): su efecto se observa en los pedidos.

### FL-PRD-002: el producto retirado no se puede pedir y una alta vieja no lo resucita

**Given**: llega `ProductUpserted` de `P2` con `version: 1`.

**When**: llega `ProductRetired` (`e-p2-ret`) con `{ productId: P2, version: 2 }`, y `ana` crea un pedido `P2 × 1` (`k-ana-d02a`).

**Then**:
1. Status `422` con code `PRODUCT_NOT_AVAILABLE`.

**When**: llega `ProductUpserted` de `P2` con `version: 1` (un alta antigua que llega tarde), y `ana` repite el pedido (`k-ana-d02b`).

**Then**:
2. Status `422` con code `PRODUCT_NOT_AVAILABLE`: la lápida no se resucita.

**When**: se **reentrega** `ProductRetired` `e-p2-ret`.

**Then**:
3. No hay efecto; un pedido de `P2` sigue respondiendo `422 PRODUCT_NOT_AVAILABLE`.

**When**: llega `ProductUpserted` de `P2` con `price: 8.00` y `version: 3` (catalog vuelve a ponerlo a la venta), y `ana` pide `P2 × 1` (`k-ana-d02c`).

**Then**:
4. Status `201` con `unitPrice: 8.00`.

### FL-PRD-003: el producto que la copia no tiene

**Given**: ningún producto publicado (arranque en frío).

**When**: `createOrder` con el token de `ana`, `k-ana-d03`, una línea `P1 × 1` y un `paymentToken`.

**Then**:
1. Status `422` con code `PRODUCT_NOT_AVAILABLE` (`onMiss: fail`), y payment no recibió ninguna llamada.

### FL-PRD-004: la retirada que llega antes que el alta

**Given**: ningún producto publicado.

**When**: llega `ProductRetired` de `P3` con `version: 2`. Después llega `ProductUpserted` de `P3` con `version: 1`. `ana` pide `P3 × 1` (`k-ana-d04`).

**Then**:
1. Status `422` con code `PRODUCT_NOT_AVAILABLE`: la retirada dejó una lápida y el alta vieja se descartó.

## Publicación fiable del encargo de correo (outbox)

### FL-OBX-001: el encargo de correo sobrevive a un canal indisponible

**Given**: el catálogo base. payment responde a `requestCharge` con `{ id: PAY1, status: "failed", failureReason: "declined" }`. El canal `notificationRequests` sin mensajes, y el canal de eventos **indisponible**.

**When**: `createOrder` con el token de `ana`, `k-ana-x01`, una línea `P1 × 1` y un `paymentToken`.

**Then**:
1. Status `201` con el cuerpo completo del pedido en `paymentFailed`: la indisponibilidad del canal no llega al cliente.
2. `getOrder` de `{o1}` devuelve `paymentFailed`.
3. El canal `notificationRequests` no ha recibido ningún mensaje todavía.

**When**: el canal vuelve a estar disponible.

**Then**:
4. En 10 s o menos, el canal `notificationRequests` recibe **exactamente un** `NotificationRequested`, con `idempotencyKey: "order:{o1}:payment-failed"` y el `correlationId` de la petición del punto anterior.
5. El servidor no ha abandonado ningún evento.

### FL-OBX-002: el encargo que el relay abandona no se pierde en silencio

**Given**: el catálogo base. payment responde a `requestCharge` con `failed` (`declined`). El canal indisponible, y `ana` crea `{o1}` (`k-ana-x02`), cuyo `NotificationRequested` queda pendiente de salir.

**When**: se agota el presupuesto de reintentos de ese evento con el canal todavía caído.

**Then**:
1. El servidor informa de **un** evento abandonado.
2. Restablecido el canal, ese `NotificationRequested` **no** se publica.
3. Y el canal no recibe ninguna otra cosa.

## Seguridad y navegador

### FL-SEC-001: sin credencial y desde el navegador

**Given**: el catálogo base.

**When**: `createOrder` (`POST /api/v1/orders`), `getOrder` (`GET /api/v1/orders/{cualquier id}`), `listMyOrders` (`GET /api/v1/orders`) y `cancelOrder` (`POST /api/v1/orders/{cualquier id}/cancel`), cada una **sin** cabecera `Authorization`.

**Then**:
1. Las cuatro responden `401` con code `UNAUTHENTICATED`, y payment no recibió ninguna llamada.

**When**: lo mismo con un token **caducado**.

**Then**:
2. Las cuatro responden `401`.

**When**: preflight desde un origen web permitido — `OPTIONS /api/v1/orders`, sin credencial, con `Access-Control-Request-Method: POST` y `Access-Control-Request-Headers: Authorization, Content-Type, Idempotency-Key`.

**Then**:
3. La respuesta acepta el método `POST` y las tres cabeceras pedidas, con `Access-Control-Max-Age: 3600`, y **sin** `Access-Control-Allow-Credentials: true`.

**When**: petición normal cross-origin desde ese origen — `createOrder` con el token de `ana`, `k-ana-s01`, una línea `P1 × 1` y un `paymentToken` (payment responde `authorized`).

**Then**:
4. Status `201`, y `Access-Control-Expose-Headers` incluye `Location` y `X-Correlation-Id`, de modo que el navegador puede leer las dos.

**Notas de determinación**: no hay roles ni permisos (`level: required` sin más), así que no hay un `403` «sin permiso» por operación. El único `403` de comprador es el de propiedad de `cancelOrder` (`ORDER_NOT_OWNED`, en FL-CAN-002).
