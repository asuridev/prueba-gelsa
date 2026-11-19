#!/usr/bin/env bash
# check-test-idioms.sh — idiomas de las pruebas de integración que no se ven al compilar.
#
# Sale 1 si encuentra algo. Lo ejecuta el agente de pruebas al cerrar, junto a
# ./gradlew compileIntegrationTestJava: ese compila, y esto mira lo que compilar no juzga.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/src/integrationTest"
[ -d "$SRC" ] || { echo "No hay src/integrationTest: nada que revisar."; exit 0; }

found=0

# Los comentarios se borran ANTES de mirar: el propio ejemplo «MAL» de la convención, pegado
# en un comentario, haría de coartada o de falso positivo según el día.
strip() { sed -e 's://.*::' "$1"; }

while IFS= read -r -d '' file; do
  # Extracción anidada dentro de una llamada que tiene sobrecargas. La regla de la convención
  # es una sola: captura primero en una variable, usa después.
  hits="$(strip "$file" | grep -nE '(String\.valueOf|\.formatted|List\.of|Map\.of)\([^)]*(jsonPath|JsonPath\.read)\(' || true)"
  if [ -n "$hits" ]; then
    found=1
    echo "[sobrecarga] ${file#"$ROOT/"}"
    echo "$hits" | sed 's/^/    /'
  fi
done < <(find "$SRC" -name '*.java' -print0)

if [ "$found" -ne 0 ]; then
  cat <<'AYUDA'

Una extracción de JSON anidada dentro de una llamada con sobrecargas deja que javac resuelva
la sobrecarga por el tipo estático e inserte un cast que revienta en RUNTIME
(ClassCastException: String cannot be cast to [C). Compila, así que no lo ve ningún gate de
compilación — por eso existe este.

  MAL:  String sku = String.valueOf(JsonPath.read(payload, "$.data.sku"));
  BIEN: Object raw = JsonPath.read(payload, "$.data.sku");
        String sku = String.valueOf(raw);

La regla y el porqué, en docs/keel/conventions/integration-tests.md.
AYUDA
  exit 1
fi

echo "Idiomas de las pruebas: OK."
