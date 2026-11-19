#!/usr/bin/env bash
# Apaga el stack de pruebas manuales. Con -v borra tambien los volumenes, que es
# lo que deja la siguiente sesion con la base de datos vacia.
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

"${COMPOSE[@]}" down "$@"
