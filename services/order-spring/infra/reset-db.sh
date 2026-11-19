#!/usr/bin/env bash
# reset-db.sh — deja el estado de prueba de order como recién arrancado:
# vacía los datos de la BD (esquema intacto), purga los destinos de mensajería (notificationRequests, order.payment, order.catalog, order.payment-dlq, order.catalog-dlq), reinicia el stub de proveedores (mappings y log de peticiones).
# Ejecutar antes de cada flujo FL-* de specs/validation-scenarios.md: los Given
# asumen estado limpio. Uso (desde la raíz; con podman, exporta CONTAINER_RUNTIME=podman):
#   bash infra/reset-db.sh
#   bash infra/reset-db.sh --schema   # además, RECREA el esquema
#
# --schema es para después de regenerar entidades: 'ddl-auto: update' no elimina
# columnas obsoletas ni afloja un NOT NULL preexistente, así que el esquema queda
# con restos que ninguna entidad mapea y todo INSERT falla con un 409 sin relación
# aparente con la causa. Recrear el esquema es la salida; el volumen no se toca.
set -u

MODE=data
case "${1:-}" in
  --schema) MODE=schema ;;
  "") ;;
  *) echo "Uso: bash infra/reset-db.sh [--schema]" >&2; exit 2 ;;
esac

RUNTIME="${CONTAINER_RUNTIME:-}"
if [ -z "$RUNTIME" ]; then
  if command -v docker >/dev/null 2>&1; then RUNTIME=docker
  elif command -v podman >/dev/null 2>&1; then RUNTIME=podman
  else echo "No se encontró docker ni podman en el PATH." >&2; exit 2; fi
fi

if [ "$MODE" = schema ]; then
  if $RUNTIME exec 'order-devtools' sh -c 'PGPASSWORD='\''changeme'\'' psql -h db -U order -d order -v ON_ERROR_STOP=1 -q -c '\''DROP SCHEMA public CASCADE; CREATE SCHEMA public;'\'''; then
    echo "Esquema recreado (PostgreSQL): lo vuelve a crear Hibernate al arrancar la app."
  else
    echo "FALLO al recrear el esquema. ¿Está la infraestructura arriba ('bash infra/up.sh')?" >&2
    exit 1
  fi
else
  if $RUNTIME exec 'order-devtools' sh -c 'PGPASSWORD='\''changeme'\'' psql -h db -U order -d order -v ON_ERROR_STOP=1 -q -c "DO \$\$ DECLARE stmt text; BEGIN SELECT '\''TRUNCATE TABLE '\'' || string_agg(quote_ident(tablename), '\'', '\'') || '\'' RESTART IDENTITY CASCADE'\'' INTO stmt FROM pg_tables WHERE schemaname = '\''public'\'' AND tablename NOT IN ('\''flyway_schema_history'\'', '\''keel_timer'\''); IF stmt IS NOT NULL THEN EXECUTE stmt; END IF; IF to_regclass('\''public.keel_timer'\'') IS NOT NULL THEN DELETE FROM keel_timer WHERE entity_id <> '\''00000000-0000-0000-0000-000000000000'\''; END IF; END \$\$;"'; then
    echo "Datos reseteados (PostgreSQL)."
  else
    echo "FALLO al resetear los datos. ¿Está la infraestructura arriba ('bash infra/up.sh')?" >&2
    exit 1
  fi
fi

if $RUNTIME exec 'order-devtools' sh -c 'curl -sf -u guest:guest -XDELETE http://rabbitmq:15672/api/queues/%2F/notificationRequests/contents'; then
  echo "Canal purgado (RabbitMQ: notificationRequests)."
else
  echo "AVISO: no se pudo purgar 'notificationRequests' (¿la cola/topic aún no existe?). Continúo." >&2
fi

if $RUNTIME exec 'order-devtools' sh -c 'curl -sf -u guest:guest -XDELETE http://rabbitmq:15672/api/queues/%2F/order.payment/contents'; then
  echo "Canal purgado (RabbitMQ: order.payment)."
else
  echo "AVISO: no se pudo purgar 'order.payment' (¿la cola/topic aún no existe?). Continúo." >&2
fi

if $RUNTIME exec 'order-devtools' sh -c 'curl -sf -u guest:guest -XDELETE http://rabbitmq:15672/api/queues/%2F/order.catalog/contents'; then
  echo "Canal purgado (RabbitMQ: order.catalog)."
else
  echo "AVISO: no se pudo purgar 'order.catalog' (¿la cola/topic aún no existe?). Continúo." >&2
fi

if $RUNTIME exec 'order-devtools' sh -c 'curl -sf -u guest:guest -XDELETE http://rabbitmq:15672/api/queues/%2F/order.payment-dlq/contents'; then
  echo "Canal purgado (RabbitMQ: order.payment-dlq)."
else
  echo "AVISO: no se pudo purgar 'order.payment-dlq' (¿la cola/topic aún no existe?). Continúo." >&2
fi

if $RUNTIME exec 'order-devtools' sh -c 'curl -sf -u guest:guest -XDELETE http://rabbitmq:15672/api/queues/%2F/order.catalog-dlq/contents'; then
  echo "Canal purgado (RabbitMQ: order.catalog-dlq)."
else
  echo "AVISO: no se pudo purgar 'order.catalog-dlq' (¿la cola/topic aún no existe?). Continúo." >&2
fi

if $RUNTIME exec 'order-devtools' sh -c 'curl -sf -o /dev/null -XPOST http://wiremock:8080/__admin/reset'; then
  echo "Stub de proveedores reiniciado (WireMock (proveedor de prueba de las integraciones salientes): mappings y log de peticiones)."
else
  echo "AVISO: no se pudo reiniciar el stub HTTP (¿está 'wiremock' arriba?). Continúo." >&2
fi
