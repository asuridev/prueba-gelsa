#!/usr/bin/env bash
# reset-db.sh — deja el estado de prueba de notifications como recién arrancado:
# vacía los datos de la BD (esquema intacto), purga los destinos de mensajería (notificationEvents, notifications.any-registered-system, notifications.any-registered-system-dlq), vacía el buzón de correo.
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
  if $RUNTIME exec 'notifications-devtools' sh -c 'PGPASSWORD='\''changeme'\'' psql -h db -U notifications -d notifications -v ON_ERROR_STOP=1 -q -c '\''DROP SCHEMA public CASCADE; CREATE SCHEMA public;'\'''; then
    echo "Esquema recreado (PostgreSQL): lo vuelve a crear Hibernate al arrancar la app."
  else
    echo "FALLO al recrear el esquema. ¿Está la infraestructura arriba ('bash infra/up.sh')?" >&2
    exit 1
  fi
else
  if $RUNTIME exec 'notifications-devtools' sh -c 'PGPASSWORD='\''changeme'\'' psql -h db -U notifications -d notifications -v ON_ERROR_STOP=1 -q -c "DO \$\$ DECLARE stmt text; BEGIN SELECT '\''TRUNCATE TABLE '\'' || string_agg(quote_ident(tablename), '\'', '\'') || '\'' RESTART IDENTITY CASCADE'\'' INTO stmt FROM pg_tables WHERE schemaname = '\''public'\'' AND tablename NOT IN ('\''flyway_schema_history'\'', '\''keel_timer'\''); IF stmt IS NOT NULL THEN EXECUTE stmt; END IF; IF to_regclass('\''public.keel_timer'\'') IS NOT NULL THEN DELETE FROM keel_timer WHERE entity_id <> '\''00000000-0000-0000-0000-000000000000'\''; END IF; END \$\$;"'; then
    echo "Datos reseteados (PostgreSQL)."
  else
    echo "FALLO al resetear los datos. ¿Está la infraestructura arriba ('bash infra/up.sh')?" >&2
    exit 1
  fi
fi

if $RUNTIME exec 'notifications-devtools' sh -c 'curl -sf -u guest:guest -XDELETE http://rabbitmq:15672/api/queues/%2F/notificationEvents/contents'; then
  echo "Canal purgado (RabbitMQ: notificationEvents)."
else
  echo "AVISO: no se pudo purgar 'notificationEvents' (¿la cola/topic aún no existe?). Continúo." >&2
fi

if $RUNTIME exec 'notifications-devtools' sh -c 'curl -sf -u guest:guest -XDELETE http://rabbitmq:15672/api/queues/%2F/notifications.any-registered-system/contents'; then
  echo "Canal purgado (RabbitMQ: notifications.any-registered-system)."
else
  echo "AVISO: no se pudo purgar 'notifications.any-registered-system' (¿la cola/topic aún no existe?). Continúo." >&2
fi

if $RUNTIME exec 'notifications-devtools' sh -c 'curl -sf -u guest:guest -XDELETE http://rabbitmq:15672/api/queues/%2F/notifications.any-registered-system-dlq/contents'; then
  echo "Canal purgado (RabbitMQ: notifications.any-registered-system-dlq)."
else
  echo "AVISO: no se pudo purgar 'notifications.any-registered-system-dlq' (¿la cola/topic aún no existe?). Continúo." >&2
fi

if $RUNTIME exec 'notifications-devtools' sh -c 'curl -sf -o /dev/null -XDELETE http://mailpit:8025/api/v1/messages && { curl -sf -o /dev/null -XPUT -H "Content-Type: application/json" -d "{}" http://mailpit:8025/api/v1/chaos || true; }'; then
  echo "Buzón de correo vaciado (Mailpit (destino SMTP de prueba))."
else
  echo "AVISO: no se pudo vaciar el buzón de correo (¿está 'mailpit' arriba?). Continúo." >&2
fi
