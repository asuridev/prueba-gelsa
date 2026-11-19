#!/usr/bin/env bash
# Prepara el realm de prueba "order" en el Keycloak de infra/docker-compose.yaml:
# realm + cliente de usuario + clientes maquina (el diseno no declara roles: las personas las da de alta el arnes con tokenAs). Generado
# por keel-spring build a partir de specs/security.keel.yaml: los nombres y secretos
# son los mismos que infra/test-credentials.env entrega a AbstractFlowIT, y por eso
# no se editan aqui a mano — si algo tiene que cambiar, cambia en el diseno.
#
# Idempotente: las creaciones toleran el 409 de Keycloak (recurso ya existente), asi
# que se puede reejecutar tras cada 'compose up'. Espera a que Keycloak acepte una
# sesion admin antes de empezar (KEEL_KC_WAIT_ATTEMPTS / KEEL_KC_WAIT_DELAY) y aborta
# si no lo consigue: aprovisionar a medias es peor que no aprovisionar.
#
# Nota: el GET client-scopes de Keycloak NO filtra por -q name=... (devuelve el
# listado completo), a diferencia de GET clients; por eso el id de un client-scope se
# busca por coincidencia exacta ",<nombre>$" sobre el csv id,name.
#
# Convenciones: docs/keel/conventions/infra-validation.md
#               references/test-clients.md de la skill keel-spring-keycloak
set -euo pipefail
export MSYS_NO_PATHCONV=1

# Runtime y frontend de compose con la MISMA lógica que up.sh, y no una propia: este
# script hardcodeaba `podman compose`, que en Windows delega en el docker-compose.exe del
# PATH y no encuentra el named pipe de la máquina de podman. El síntoma era «Keycloak no
# aceptó una sesión admin tras N intentos» con Keycloak perfectamente sano — un falso
# negativo que acusa al servidor de un problema que es del frontend de compose.
RUNTIME="${CONTAINER_RUNTIME:-}"
if [ -z "$RUNTIME" ]; then
  if command -v docker >/dev/null 2>&1; then RUNTIME=docker
  elif command -v podman >/dev/null 2>&1; then RUNTIME=podman
  else echo "No se encontró docker ni podman en el PATH." >&2; exit 2; fi
fi

COMPOSE=()
if [ "$RUNTIME" = "podman" ] && ! podman compose ls >/dev/null 2>&1; then
  # El frontend delegado no alcanza el motor: podman-compose es un binario aparte
  # que habla con podman directamente.
  if command -v podman-compose >/dev/null 2>&1; then
    COMPOSE=(podman-compose -f infra/docker-compose.yaml)
  else
    echo "podman no puede ejecutar compose ('podman compose ls' falla) y no encuentro podman-compose." >&2
    echo "Instala podman-compose ('pip install podman-compose') o arranca la máquina ('podman machine start')." >&2
    exit 2
  fi
else
  COMPOSE=("$RUNTIME" compose -f infra/docker-compose.yaml)
fi

KC="${COMPOSE[*]} exec -T keycloak /opt/keycloak/bin/kcadm.sh"
REALM=order
SVC=order                     # audiencia del servicio (security.serviceAuth.audience)
USER_CLIENT=order-spring-test # cliente publico para tokens de usuario
PASSWORD=password

# El script es IDEMPOTENTE: re-ejecutarlo sobre un realm ya sembrado devuelve 409 en
# cada `create`, y eso es normal. Lo que NO es normal es cualquier otro fallo de kcadm,
# y la versión anterior de este helper —un `|| true` incondicional— los tragaba todos:
# el aprovisionamiento quedaba a medias, el script salía 0, y el defecto reaparecía
# minutos después como un 403 sin explicación dentro de un test de integración. Se
# tolera el conflicto, que es la única forma de fallo esperada, y se aborta con el resto.
#
# OJO con la forma de capturar el codigo de salida. `out=$(...); rc=$?` NO sirve bajo
# `set -e`: una asignacion por sustitucion de comandos toma el estado de salida de la
# sustitucion, asi que un kcadm que devuelve != 0 —incluido el 409 que esta funcion existe
# para tolerar— dispara errexit en la propia asignacion y jamas se llega a leer `rc`. El
# efecto observado fue el peor posible: reejecutar el script sobre un realm ya sembrado
# abortaba en silencio justo despues de crear el realm, dejando usuarios, roles y clientes
# sin aprovisionar, con exit 1 y sin una sola linea de ERROR. La forma segura es capturar
# el codigo en la MISMA sentencia, que es un contexto de condicion y suspende errexit.
run() {
  out=$(eval "$KC $*" 2>&1) && rc=0 || rc=$?
  printf '%s\n' "$out" | grep -v "compose provider\|^\[0m$\|^$" || true
  if [ "$rc" -ne 0 ] && ! printf '%s' "$out" | grep -qi "409\|already exists\|exists with same\|Conflict"; then
    echo "ERROR: kcadm falló ($rc) en: $*" >&2
    exit 1
  fi
}
# Los dos helpers de abajo terminan en un pipeline con grep/tail: bajo `pipefail` un «no
# encontrado» —que es un resultado legitimo, no un fallo— devuelve != 0 y mataria el script
# en la asignacion que lo llama, otra vez sin mensaje. Se cierra con `|| true` y el vacio se
# juzga en require_id, que si dice QUE no se pudo resolver: morir es correcto aqui, morir
# callado no.
# id de un cliente por su clientId exacto (GET clients SI soporta -q).
client_id_of() { eval "$KC get clients -r $REALM -q clientId=$1 --fields id --format csv --noquotes" 2>/dev/null | tr -d '\r' | tail -1 || true; }
# id de un client-scope por su name exacto (GET client-scopes NO soporta -q: filtra en local).
scope_id_of() { eval "$KC get client-scopes -r $REALM --fields id,name --format csv --noquotes" 2>/dev/null | tr -d '\r' | grep ",$1\$" | cut -d, -f1 || true; }
# id de un usuario por su username EXACTO. GET users?username=x busca por SUBCADENA:
# 'editor' casa tambien con 'editor-2', y quedarse con la ultima fila escribia el atributo
# del claim en el usuario '-2' y dejaba al original sin claim (corrida notifications,
# 2026-09-30: 20 escenarios en 403). Se pide exact=true Y se filtra en local por la columna
# del username: lo segundo no depende de que la version de Keycloak honre lo primero.
user_id_of() { eval "$KC get users -r $REALM -q username=$1 -q exact=true --fields id,username --format csv --noquotes" 2>/dev/null | tr -d '\r' | grep ",$1\$" | head -1 | cut -d, -f1 || true; }
# Un id vacio significa que el recurso no esta donde deberia: el aprovisionamiento va a medias
# y seguir solo produce peticiones malformadas contra rutas con un id en blanco.
require_id() {
  if [ -z "$1" ]; then
    echo "ERROR: no se pudo resolver $2 — el realm '$REALM' esta aprovisionado a medias." >&2
    echo "  Revisa la salida anterior; si Keycloak se reinicio, vuelve a ejecutar este script." >&2
    exit 1
  fi
}

echo "== Sesion admin (espera a que Keycloak acepte kcadm) =="
KC_WAIT_ATTEMPTS="${KEEL_KC_WAIT_ATTEMPTS:-60}"
KC_WAIT_DELAY="${KEEL_KC_WAIT_DELAY:-2}"
attempt=1
while :; do
  if eval "$KC config credentials --server http://localhost:8080 --realm master --user admin --password admin" >/dev/null 2>&1; then
    echo "  sesion admin establecida (intento $attempt)"
    break
  fi
  if [ "$attempt" -ge "$KC_WAIT_ATTEMPTS" ]; then
    echo "Keycloak no acepto una sesion admin tras $KC_WAIT_ATTEMPTS intentos ($((KC_WAIT_ATTEMPTS * KC_WAIT_DELAY))s)." >&2
    echo "Diagnostica con: ${COMPOSE[*]} logs keycloak" >&2
    exit 1
  fi
  attempt=$((attempt + 1))
  sleep "$KC_WAIT_DELAY"
done

echo "== Realm =="
run "create realms -s realm=$REALM -s enabled=true"

echo "== Cliente publico para tokens de usuario (password grant) =="
run "create clients -r $REALM -s clientId=$USER_CLIENT -s enabled=true -s publicClient=true -s directAccessGrantsEnabled=true"

echo "Realm '$REALM' listo. Verifica con:"
echo "  curl -s -u 'CLIENTE:<secreto de test-credentials.env>' -d 'grant_type=client_credentials' http://localhost:8180/realms/order/protocol/openid-connect/token | jq -r .access_token"
