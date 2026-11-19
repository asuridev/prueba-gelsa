#!/usr/bin/env bash
# Apaga el sistema completo. Con -v borra también los volúmenes (la base de datos), y el
# siguiente `bash up.sh` vuelve a sembrar desde cero.
set -euo pipefail
cd "$(dirname "$0")"

RUNTIME="${CONTAINER_RUNTIME:-}"
if [ -z "$RUNTIME" ]; then
  if command -v docker >/dev/null 2>&1; then RUNTIME=docker
  elif command -v podman >/dev/null 2>&1; then RUNTIME=podman
  else echo "No se encontró docker ni podman en el PATH." >&2; exit 2; fi
fi

if [ "$RUNTIME" = "podman" ]; then
  if command -v podman-compose >/dev/null 2>&1; then COMPOSE=(podman-compose)
  else COMPOSE=(podman compose); fi
else
  COMPOSE=(docker compose)
fi

"${COMPOSE[@]}" down "$@"
