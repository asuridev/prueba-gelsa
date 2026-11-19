# Informe de generación — payment v0.1.4

## 0. Matriz final (`bash infra/score-scenarios.sh`)

```
RESULTADO: OK — 31 escenario(s) al 100%.
EXIT=0
```

Generación en dos pasadas: v0.1.3 (24/31 a la primera, 30/31 tras corregir el barrido; el fallo restante, `FL-QRY-001`, era un
`Then` que contradecía el contrato de cable y lo corrigió el diseñador) y evolución a v0.1.4 (31/31).

## Hallazgos que son del generador, no de este proyecto

Los huecos de diseño no existen: `design-gaps.yaml` no se escribe.

1. **Puerto `PaymentGateway.savePaymentMethod` incompleto (keel-spring).** Devolvía solo un `String` y el adaptador
   `StripePaymentGateway` descartaba la respuesta del `attach`: no se puede rellenar `card` (brand, last4, caducidad) ni distinguir
   «rechazado» de «exige 3DS». Se amplió en el proyecto (`SavedPaymentMethod`, `PaymentMethodNotAcceptedException`).
   Editado en un archivo generado: el próximo `--refresh` puede marcarlo como fusión pendiente. Debe corregirse en el generador.
2. **Cliente HTTP del JDK reintenta los GET sin respuesta.** Duplicaba las consultas de estado a la pasarela. Se fijó
   `jdk.httpclient.redirects.retrylimit=1` en `PaymentGatewayHttpConfig` y `PaymentApplication` (propiedad global del proceso). El
   generador debería cablearlo en el cliente de la pasarela.
3. **`claimAwaiting` heredaba la transacción de solo lectura del adaptador de repositorio** (`@Transactional(readOnly = true)` a nivel
   de clase): un método `@Modifying` generado sin `@Transactional` propio falla. Corregido en `PaymentRepositoryImpl`.
4. **Primitivas que faltan en el arnés de pruebas** (aproximadas con stubs a mano en `PaymentFlowSupport`/las clases de flujo):
   `gatewaySavesPaymentMethod` con datos de tarjeta, rechazo por autenticación al guardar, devolución con importe concreto,
   anulación que contesta «autenticación fallida» y pasarela que retiene su respuesta hasta que llega el aviso (FL-STL-009).
   No hubo `harnessPatches`.
5. **`PaymentReconciliation` generado:** ante `NOT_FOUND`, un cobro en `capturing` o `refunding` queda como estaba (`markFailed` solo
   actúa desde `pending`, `actionRequired` o `canceling`). Caso anómalo no cubierto; sin tocar.

## Pendiente del diseñador

- **Prueba en vivo del baseline** (`baselineTested: PENDING`): `bash infra/down.sh --volumes`, `bash infra/up.sh`,
  `PROFILE=local,migrations ./gradlew bootRun`.
