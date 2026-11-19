# Informe de generación — notifications-spring

## 0. Matriz final de `infra/score-scenarios.sh`

Código de salida: **1** — la generación **NO** pasa el gate (exige 100% de escenarios en OK).

```
MATRIZ
  OK       FL-BNC-001           BounceFlowIT
  OK       FL-BNC-002           BounceConcurrentFlowIT
  OK       FL-DSP-001           DispatchDrainFlowIT
  OK       FL-DSP-010           DispatchSuppressedFlowIT
  OK       FL-DSP-020           DispatchRejectedFlowIT
  OK       FL-DSP-021           DispatchPartialRejectionFlowIT
  OK       FL-DSP-022           DispatchRelayDownFlowIT
  OK       FL-DSP-030           DispatchRenderFlowIT
  OK       FL-DSP-040           DispatchClusterFlowIT
  OK       FL-EVT-001           EventRequestFlowIT
  OK       FL-EVT-002           EventDeadLetterFlowIT
  OK       FL-EVT-003           EventRetryReinjectFlowIT
  OK       FL-MSG-001           MessageQueryFlowIT
  OK       FL-MSG-002           MessagePaginationFlowIT
  OK       FL-NTF-001           NotificationRequestFlowIT
  OK       FL-NTF-002           NotificationIdempotencyFlowIT
  OK       FL-NTF-003           NotificationRaceFlowIT
  OK       FL-NTF-010           NotificationRejectionsFlowIT
  OK       FL-NTF-020           NotificationRetiredTemplateFlowIT
  OK       FL-NTF-030           NotificationCredentialsFlowIT
  OK       FL-NTF-040           NotificationSuppressionScanFlowIT
  OK       FL-OBX-001           OutboxUnavailableChannelFlowIT
  OK       FL-OBX-002           OutboxAbandonedEventFlowIT
  OK       FL-PRG-001           PersonalDataPurgeFlowIT
  OK       FL-RSC-001           RescueStuckFlowIT
  OK       FL-RSC-002           RescueFreshFlowIT
  OK       FL-SEC-001           CorsPreflightFlowIT
  OK       FL-SEC-002           CorsCrossOriginFlowIT
  OK       FL-SEC-003           AuthorizationFlowIT
  OK       FL-SND-001           SenderSetupFlowIT
  OK       FL-SND-002           SenderConcurrentFlowIT
  OK       FL-SND-003           SenderFrozenFlowIT
  OK       FL-SUP-001           SuppressionLifecycleFlowIT
  OK       FL-SUP-002           SuppressionConcurrentFlowIT
  FALLO    FL-SUP-003           SuppressionListingFlowIT
  OK       FL-TPL-001           TemplateCreationFlowIT
  OK       FL-TPL-010           TemplatePublishFlowIT
  OK       FL-TPL-011           TemplateConcurrentPublishFlowIT
  OK       FL-TPL-020           TemplateRevertRetireFlowIT
  FALLO    FL-TPL-030           TemplateListingFlowIT

RESULTADO: KO — 38 OK · 2 FALLO · 0 omitido(s) · 0 no ejercitado(s).
```

## Estado

Se detiene en la fase 2b. No se ejecutó la fase 3 (calidad, baseline de migraciones, gates de
idempotencia/logging/telemetría), ni el commit. Falta por cerrar, en esa fase, el baseline
`V1__baseline_schema.sql` y la guía de despliegue productivo del `README.md`.

## Fallos arbitrados (`keel-spring-validate`): ambos `culprit: design`

| Escenario | Petición | Respuesta del servidor | El Then esperaba |
|---|---|---|---|
| FL-TPL-030 (Then 4) | `GET /api/v1/templates?page=5` | 200, `items: []`, `totalElements: 0`, `totalPages: 0` | `totalElements: 3`, `totalPages: 1` |
| FL-SUP-003 (Then 3) | `GET /api/v1/suppressions?page=4` | 200, `items: []`, `totalElements: 0`, `totalPages: 0` | `totalElements: 3` |

Causa: los dos `Then` contradicen el contrato canónico de paginación del DSL
(`docs/dsl/api.md:43-45`, `docs/keel/wire-contract.md:96-98`): una página vacía **o fuera de rango**
lleva `totalElements: 0` y `totalPages: 0`. El servidor lo cumple en `PagedResponse.java:16-17`, que
es salida intacta de `build` (su sha256 coincide con `keel-generated.json`). El origen es el
preámbulo de `specs/validation-scenarios.md:52-53`, que limita esos ceros a "si no hay ningún
elemento" y de ahí derivaron los dos `Then`.

No es de la prueba (traducen su `Then` al pie de la letra), ni del arnés, ni del código: cambiar
`PagedResponse` para que pasen rompería el contrato canónico. Detalle en `design-gaps.yaml`.

## Cómo desbloquearlo

1. En el workspace de diseño, `/keel-evolve specs/notifications` (patch) para regenerar
   `validation-scenarios.md` con los tres cambios de `design-gaps.yaml`.
2. `keel-spring build specs/notifications --refresh --prune` desde el workspace.
3. Aquí, `/keel-generate-spring` en modo evolución: `keel-spring-tests` solo para
   `TemplateListingFlowIT` y `SuppressionListingFlowIT`, y `bash infra/score-scenarios.sh` completo.

Alternativa, solo si el diseñador quiere de verdad totales reales fuera de rango: es un cambio del
DSL y de todos los generadores, no de este servicio.

## Defectos del generador

Ninguno detectado: sin `harnessPatches`, sin fallos `culprit: harness`, sin falsos negativos de
infraestructura.
