# Informe de generación — order-spring

Generado desde `specs/order` v0.1.0 con keel-spring 0.4.0.

## 0. Matriz final (`bash infra/score-scenarios.sh`)

Salida literal de la última puntuación, y su código de salida:

```
Humo del arnés: OK.
Ejecutando la suite completa…

MATRIZ
  OK  FL-CAN-001 … FL-CAN-006      (6)
  OK  FL-OBX-001, FL-OBX-002       (2)
  OK  FL-ORD-001 … FL-ORD-013      (13)
  OK  FL-PAY-001 … 005, 007        (6)
  OK  FL-PRD-001 … FL-PRD-004      (4)
  OK  FL-REC-001 … 005, 007        (6)
  OK  FL-SEC-001                   (1)

RESULTADO: OK — 38 escenario(s) al 100%.
EXIT=0
```

(Las 38 líneas `OK` se resumen por familia; ninguna salió en FALLO, OMITIDO ni NO_EJERCITADO.)

Historial de pasadas: 1.ª, 4 OK · 33 FALLO · 1 NO_EJERCITADO (causa sistémica de configuración más un defecto del arnés); 2.ª, 35 OK · 3 FALLO; 3.ª, 38 OK. Ciclos por fallo puntual consumidos: 1 (FL-ORD-011).

Esto es lo que apareció durante la generación y **no es de este proyecto, sino del generador**.

## 1. Defectos del andamiaje del arnés (`harnessPatches`)

1. **`AbstractFlowIT.resetState()` no limpiaba `SEQUENCED`.** `reset-db.sh` reinicia WireMock pero no el `Set` estático de `stubSequence`; la segunda clase que programaba una secuencia sobre `POST /payments` en la misma JVM reventaba en `@BeforeAll` (Can003, Ord004, Ord006). Parche: `resetState()` termina con `SEQUENCED.clear()`.
2. **Los helpers de métricas leían un endpoint que el `SecurityConfig` generado no deja público.** `deadLetteredEvents()`, `queryCount()` y `queryExecutions()` usaban `/actuator/metrics/*` (401). Además, `exchange()` fija `Accept: application/json` y `/actuator/prometheus` solo sirve `text/plain` (500). Parche: leer `/actuator/prometheus` con `Accept: text/plain` y sumar las series con un helper `prometheusSum`. `hibernate_statements{status="prepared"}` no se vio en vivo (ningún escenario lo usa).
3. **Falta `stubForDelayed(method, pathPattern, status, jsonBody, delayMs)` en `AbstractFlowIT`.** La base solo tiene `StubResponse.timeout`, que devuelve cuerpo vacío; FL-ORD-008 y FL-CAN-006 piden una respuesta válida con retardo. Está implementado provisionalmente en `OrderFlowSupport`.

## 2. Defectos del scaffolding de `build`

- `parameters/local/http-clients.yaml` y `develop` heredaban `token-uri: ${IDP_TOKEN_URL}` sin valor definido por nadie (el arnés solo trae `AUTH_TOKEN_URL`): todo `POST /api/v1/orders` daba 500. Corregido con `token-uri` literal a un mapping de WireMock (`infra/http-stubs/mappings/idp-token.json`) en local y default en develop. El generador debería emitir ese stub cuando el diseño declara auth saliente M2M.
- `OrderLineDto` salió sin `orderId`, que el contrato exige; se añadió al DTO y al mapper.
- `ProductRepository` y su adaptador traían un `findById` duplicado que no compilaba.
- `reconciliation.yaml` se genera con `unanswered-after-seconds` que ningún código lee (ver `design-gaps.yaml`).
- La frontera transaccional única del `UseCaseMediator` no admite «confirmar antes de llamar a payment»: createOrder y cancelOrder usan `dispatchWithoutTransaction`.
- `OutboxDispatcherFallbackConfig` se dejó intacto: su bean cede el paso al dispatcher real.

## 3. Alcance que el pipeline no cubre

- **Escenarios con ramas sin cubrir:** FL-ORD-005 (rama «no se puede obtener el token M2M», no determinista desde fuera) y FL-PAY-001 (caso borde `maxAttempts: 5` / dead letter ante fallo pasajero del almacén, exige tirar la BD a mitad de un evento).
- **Baseline de migraciones:** `V1__baseline_schema.sql` entregado y verificado en estático (`baseline: OK`), `baselineTested: PENDING`. La prueba en vivo sobre una BD sin esquema es del diseñador (ver «Despliegue en producción» del `README.md`).
- **Token caducado en FL-SEC-001:** se usa un JWT fabricado con `exp` pasado; el 401 puede ser por firma o por caducidad.
- **Claim `email`:** `customerEmail` se toma del claim estándar `email` del token (`CallerIdentity.claim`); los escenarios lo ejercitan con tokens reales de Keycloak.

## 4. Huecos del diseño

Los siete huecos están en `design-gaps.yaml` (tres de reconciliación/transacción y cuatro de escenarios: FL-REC-002/004/005 sin líneas en el Given y FL-ORD-006 sin `paymentId` en el Then).
