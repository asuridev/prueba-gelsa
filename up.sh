#!/usr/bin/env bash
# Levanta el sistema completo (order + payment + notifications) para validar el flujo
# haciendo peticiones solo a order. Sirve con docker o con podman: el runtime se detecta
# solo, o se fuerza con CONTAINER_RUNTIME.
#
# Pasos: construye las tres imágenes, arranca docker-compose.yaml y espera a que termine
# la siembra. Después, las peticiones de ejemplo están en compose/requests.http.
set -euo pipefail
export MSYS_NO_PATHCONV=1

HERE="$(cd "$(dirname "$0")" && pwd)"
cd "$HERE"

# .env es opcional (puertos, Stripe real en lugar del simulado...). Se carga también aquí para
# imprimir al final los puertos reales.
if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

RUNTIME="${CONTAINER_RUNTIME:-}"
if [ -z "$RUNTIME" ]; then
  if command -v docker >/dev/null 2>&1; then RUNTIME=docker
  elif command -v podman >/dev/null 2>&1; then RUNTIME=podman
  else echo "No se encontró docker ni podman en el PATH." >&2; exit 2; fi
fi

hostpath() {
  if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else printf '%s' "$1"; fi
}

# Con podman, 'podman compose' delega en podman-compose si está instalado.
if [ "$RUNTIME" = "podman" ]; then
  if command -v podman-compose >/dev/null 2>&1; then COMPOSE=(podman-compose)
  else COMPOSE=(podman compose); fi
  # Formato docker: el OCI por defecto descarta el HEALTHCHECK del Dockerfile, y sin él
  # ningún servicio pasa a healthy y la siembra no arranca nunca.
  BUILD_FORMAT=(--format docker)
else
  COMPOSE=(docker compose)
  BUILD_FORMAT=()
fi

# Las imágenes se construyen aquí y no con 'compose up --build': cada Dockerfile vive en
# deploy/ con el contexto en la raíz del servicio, y podman-compose ignora la clave 'dockerfile'.
for svc in order payment notifications; do
  echo "== Construyendo $svc-spring-deploy:latest"
  "$RUNTIME" build ${BUILD_FORMAT[@]+"${BUILD_FORMAT[@]}"} \
    -f "$(hostpath "$HERE/services/$svc-spring/deploy/Dockerfile")" \
    -t "$svc-spring-deploy:latest" "$(hostpath "$HERE/services/$svc-spring")"
done

echo "== Levantando el sistema (${COMPOSE[*]})"
"${COMPOSE[@]}" up -d

# La siembra corre cuando order, payment y notifications están sanos. Se espera a que
# termine para que, al salir de aquí, el sistema esté listo para recibir pedidos.
echo "== Esperando a la siembra (los servicios tardan en arrancar la primera vez)"
SEED=$("$RUNTIME" ps -a --format '{{.Names}}' | grep -E 'seed' | head -1 || true)
for _ in $(seq 1 120); do
  SEED=${SEED:-$("$RUNTIME" ps -a --format '{{.Names}}' | grep -E 'seed' | head -1 || true)}
  if [ -n "$SEED" ]; then
    STATE=$("$RUNTIME" inspect -f '{{.State.Status}}' "$SEED" 2>/dev/null || true)
    if [ "$STATE" = "exited" ]; then break; fi
  fi
  sleep 5
done

if [ -z "$SEED" ]; then
  echo "No encuentro el contenedor de la siembra. Mira: ${COMPOSE[*]} ps" >&2
  exit 1
fi
"$RUNTIME" logs "$SEED"
EXIT=$("$RUNTIME" inspect -f '{{.State.ExitCode}}' "$SEED" 2>/dev/null || echo 1)
if [ "$EXIT" != "0" ]; then
  echo "La siembra falló (código $EXIT). Revisa los logs de arriba y: ${COMPOSE[*]} logs order payment notifications" >&2
  exit 1
fi

echo ""
echo "Sistema listo."
echo "  order          http://localhost:${ORDER_PORT:-8081}   (Swagger: /swagger-ui/index.html)"
echo "  payment        http://localhost:${PAYMENT_PORT:-8082}"
echo "  notifications  http://localhost:${NOTIFICATIONS_PORT:-8083}"
echo "  Keycloak       http://localhost:${KEYCLOAK_PORT:-8180}   (admin/admin)"
echo "  RabbitMQ       http://localhost:${RABBITMQ_UI_PORT:-15672}  (guest/guest)"
echo "  Mailpit        http://localhost:${MAILPIT_UI_PORT:-8025}   (correos enviados)"
if [ -z "${STRIPE_BASE_URL:-}" ]; then
  echo "  Stripe         simulado: http://localhost:${STRIPE_MOCK_PORT:-8099}/__admin/requests (llamadas recibidas)"
else
  echo "  Stripe         $STRIPE_BASE_URL"
fi
echo ""
echo "Peticiones de ejemplo: compose/requests.http"
