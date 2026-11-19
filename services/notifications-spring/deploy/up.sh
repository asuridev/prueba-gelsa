#!/usr/bin/env bash
# Levanta el servicio en contenedor para probarlo a mano. Sirve igual con docker
# que con podman: el runtime se detecta solo (o se fuerza con CONTAINER_RUNTIME).
#
# No es la infraestructura de la generacion: esa es infra/, y no conviene tener las
# dos arriba a la vez porque publican los mismos puertos.
set -euo pipefail
export MSYS_NO_PATHCONV=1

RUNTIME="${CONTAINER_RUNTIME:-}"
if [ -z "$RUNTIME" ]; then
  if command -v docker >/dev/null 2>&1; then RUNTIME=docker
  elif command -v podman >/dev/null 2>&1; then RUNTIME=podman
  else echo "No se encontró docker ni podman en el PATH." >&2; exit 2; fi
fi

HERE="$(cd "$(dirname "$0")" && pwd)"
COMPOSE_FILE="$HERE/docker-compose.yaml"
ENV_FILE="$HERE/.env"

# Los mismos valores que lee compose, tambien como variables del script: es lo que
# permite sondear e imprimir el puerto REAL cuando alguien lo ha cambiado en .env.
if [ -f "$ENV_FILE" ]; then
  set -a
  # shellcheck disable=SC1090
  . "$ENV_FILE"
  set +a
fi

hostpath() {
  if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else printf '%s' "$1"; fi
}

COMPOSE=()
if [ "$RUNTIME" = "podman" ] && ! podman compose ls >/dev/null 2>&1; then
  # El frontend delegado no alcanza el motor: podman-compose es un binario aparte
  # que habla con podman directamente.
  if command -v podman-compose >/dev/null 2>&1; then
    COMPOSE=(podman-compose -f "$(hostpath "$COMPOSE_FILE")" --env-file "$(hostpath "$ENV_FILE")")
  else
    echo "podman no puede ejecutar compose ('podman compose ls' falla) y no encuentro podman-compose." >&2
    echo "Instala podman-compose ('pip install podman-compose') o arranca la máquina ('podman machine start')." >&2
    exit 2
  fi
else
  COMPOSE=("$RUNTIME" compose -f "$(hostpath "$COMPOSE_FILE")" --env-file "$(hostpath "$ENV_FILE")")
fi

# La imagen de la app se construye AQUI y no con 'compose up --build': el contexto es la raiz
# del proyecto y el Dockerfile vive en deploy/, y esa combinacion necesita la clave 'dockerfile',
# que podman-compose ignora (aborta con "no Containerfile or Dockerfile specified or found").
#
# Con podman, en formato docker: el OCI por defecto DESCARTA el HEALTHCHECK del Dockerfile (solo lo avisa con un
# warning entre las lineas del build), y sin el la app no pasa nunca a healthy para quien la espere. Medido con
# podman 6 en Windows.
BUILD_FORMAT=()
if [ "$RUNTIME" = "podman" ]; then BUILD_FORMAT=(--format docker); fi
echo "== Construyendo la imagen de la app ($RUNTIME) =="
"$RUNTIME" build ${BUILD_FORMAT[@]+"${BUILD_FORMAT[@]}"} -f "$(hostpath "$HERE/Dockerfile")" -t "notifications-spring-deploy:latest" "$(hostpath "$HERE/..")"

# Cuantos puertos publica la app, y por que depende del runtime: replicar exige un RANGO de
# puertos de host (cada replica toma el siguiente libre) y podman no los admite contra un puerto
# unico del contenedor. Con docker se usa el rango; con podman, un puerto y una sola instancia.
REPLICAS="${APP_REPLICAS:-1}"
if [ "$RUNTIME" = "docker" ]; then
  export APP_PORTS="${APP_PORT:-8080}-${APP_PORT_MAX:-8089}"
else
  export APP_PORTS="${APP_PORT:-8080}"
  if [ "$REPLICAS" -gt 1 ]; then
    echo "podman no publica rangos de puertos, asi que no puede levantar $REPLICAS replicas con el" >&2
    echo "puerto publicado. Opciones: usar docker (CONTAINER_RUNTIME=docker), o levantar una sola" >&2
    echo "instancia aqui y arrancar las demas sin publicar puerto:" >&2
    echo "  podman run -d --network <red-del-proyecto> --env-file deploy/.env notifications-spring-deploy:latest" >&2
    exit 2
  fi
fi

echo "== Levantando (${COMPOSE[0]}, $REPLICAS instancia(s) de la app) =="
"${COMPOSE[@]}" up -d --scale app="$REPLICAS"

# Espera activa a que la app responda. 'Up' no es 'listo': la JVM tarda, y con
# persistencia ademas hay que aplicar las migraciones antes de aceptar trafico.
#
# Se sondea el endpoint publicado desde el host, no el estado del healthcheck via
# 'compose ps': el formato de esa salida y el nombre que cada frontend le da al
# contenedor no son los mismos en docker que en podman-compose, y el sondeo HTTP
# comprueba ademas lo unico que le importa a quien va a probar el servicio — que
# responde por el puerto que tiene delante.
READY_URL="http://localhost:${APP_PORT:-8080}/actuator/health/readiness"
RETRIES="${KEEL_CHECK_RETRIES:-60}"
DELAY="${KEEL_CHECK_DELAY:-5}"

probe() {
  if command -v curl >/dev/null 2>&1; then
    curl -sf "$READY_URL" 2>/dev/null
  else
    wget -q -O - "$READY_URL" 2>/dev/null
  fi
}

attempt=1
while :; do
  if probe | grep -q '"status":"UP"'; then
    break
  fi
  if [ "$attempt" -ge "$RETRIES" ]; then
    echo "La app no respondio UP en $READY_URL tras $((RETRIES * DELAY))s." >&2
    echo "Mira que paso con: ${COMPOSE[*]} logs app" >&2
    exit 1
  fi
  attempt=$((attempt + 1))
  sleep "$DELAY"
done

echo ""
echo "Servicio listo."
echo "  API                    http://localhost:${APP_PORT:-8080}"
echo "  Swagger UI             http://localhost:${APP_PORT:-8080}/swagger-ui/index.html"
echo "  Health                 http://localhost:${APP_PORT:-8080}/actuator/health"
echo "  RabbitMQ (guest/guest) http://localhost:${RABBITMQ_UI_PORT:-15672}"
echo "  Keycloak (admin/admin) http://localhost:${KEYCLOAK_PORT:-8180}"
echo "  Grafana (telemetría)   http://localhost:${GRAFANA_PORT:-3001}"
echo "  Panel del servicio     http://localhost:${GRAFANA_PORT:-3001}/d/keel-notifications"
echo "  Alertas                http://localhost:${GRAFANA_PORT:-3001}/alerting/list"
echo "  Alertas recibidas (sumidero) http://localhost:${ALERT_SINK_PORT:-8091}/__admin/requests"
echo ""
echo "Usuarios del realm 'notifications' (password: password): notifications-admin, notifications-auditor, notifications-operator, template-editor, notifications-admin-2, notifications-auditor-2, notifications-operator-2, template-editor-2, no-role"
echo "Cliente publico para pedir el token: notifications-spring-test"
echo ""
echo "Postman: importa deploy/postman/notifications-local.postman_environment.json y las colecciones de docs/postman/,"
echo "selecciona el environment y ejecuta primero la coleccion de auth."
echo ""
echo "Para apagarlo: bash deploy/down.sh   (con -v ademas borra los datos)"
