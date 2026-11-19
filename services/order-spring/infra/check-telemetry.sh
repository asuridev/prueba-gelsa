#!/usr/bin/env bash
# check-telemetry.sh — cardinalidad de las métricas de order.
#
# Una etiqueta de métrica con muchos valores distintos multiplica las series: con el id del pedido
# dentro, tantas series como pedidos. No falla nada —ni excepción, ni log, ni escenario rojo—; se
# ve en la factura del backend, o cuando el sistema de métricas deja de responder.
#
# Por eso las claves de etiqueta son una lista CERRADA, la del vocabulario que estampa build. Lo
# que lleva un identificador va al SPAN, con addHighCardinalityKeyValue, que este gate no toca.
#
# Uso (desde la raíz del proyecto; no necesita infraestructura ni compilar):
#   bash infra/check-telemetry.sh
#
# Código de salida:
#   0  sin hallazgos
#   1  hay hallazgos → vuelven al agente de código
set -u

SRC="src/main/java"
if [ ! -d "$SRC" ]; then
  echo "Ejecuta el script desde la raíz del proyecto (no se encontró $SRC)." >&2
  exit 1
fi

findings=0
detail=""

# Sin comentarios: los javadoc que build deja explican estas mismas formas, y sin quitarlos el
# gate saldría rojo por su propia prosa.
while IFS= read -r file; do
  [ -n "$file" ] || continue
  hits="$(sed -e 's://.*::' -e '/^[[:space:]]*\*/d' -e '/^[[:space:]]*\/\*/d' "$file"     | grep -nE -- '([.](lowCardinalityKeyValue|tag|tags)[[:space:]]*[(]|(Tag|Tags)[.]of[[:space:]]*[(])[[:space:]]*"'     | grep -vE -- '"(keel[.]operation|keel[.]outcome|keel[.]event[.]type|keel[.]storage[.]operation|keel[.]storage[.]bucket|keel[.]timer[.]kind)"' || true)"
  if [ -n "$hits" ]; then
    findings=$((findings + 1))
    detail="$detail  [cardinality] ${file#./}: etiqueta de métrica con una clave fuera del vocabulario (keel.operation, keel.outcome, keel.event.type, keel.storage.operation, keel.storage.bucket, keel.timer.kind). Si el valor identifica algo —un id, una clave, un correo—, va al span con addHighCardinalityKeyValue, no a la métrica
$(printf '%s
' "$hits" | sed 's/^/      /')
"
  fi
done <<EOF
$(grep -rlE -- '([.](lowCardinalityKeyValue|tag|tags)[[:space:]]*[(]|(Tag|Tags)[.]of[[:space:]]*[(])[[:space:]]*"' "$SRC" 2>/dev/null)
EOF

while IFS= read -r file; do
  [ -n "$file" ] || continue
  hits="$(sed -e 's://.*::' -e '/^[[:space:]]*\*/d' -e '/^[[:space:]]*\/\*/d' "$file" \
    | grep -nE -- '[.](counter|timer|summary|gauge)[[:space:]]*[(][[:space:]]*"[^"]*"[[:space:]]*,[[:space:]]*"' \
    | grep -vE -- '[.](counter|timer|summary|gauge)[[:space:]]*[(][[:space:]]*"[^"]*"[[:space:]]*,[[:space:]]*"(keel[.]operation|keel[.]outcome|keel[.]event[.]type|keel[.]storage[.]operation|keel[.]storage[.]bucket|keel[.]timer[.]kind)"' || true)"
  if [ -n "$hits" ]; then
    findings=$((findings + 1))
    detail="$detail  [cardinality] ${file#./}: etiqueta de métrica (en la forma counter/timer/summary/gauge con pares clave-valor) con una clave fuera del vocabulario (keel.operation, keel.outcome, keel.event.type, keel.storage.operation, keel.storage.bucket, keel.timer.kind). Si el valor identifica algo, va al span con addHighCardinalityKeyValue, no a la métrica\n$(printf '%s\n' "$hits" | sed 's/^/      /')\n"
  fi
done <<EOF
$(grep -rlE -- '[.](counter|timer|summary|gauge)[[:space:]]*[(][[:space:]]*"[^"]*"[[:space:]]*,[[:space:]]*"' "$SRC" 2>/dev/null)
EOF

echo ""
echo "CARDINALIDAD DE LAS MÉTRICAS"
if [ "$findings" -eq 0 ]; then echo "  cardinality          OK"; else echo "  cardinality          KO"; fi

if [ "$findings" -gt 0 ]; then
  echo ""
  echo "HALLAZGOS ($findings)"
  printf '%b' "$detail"
  echo ""
  echo "Ver docs/keel/conventions/observability.md § Reglas para quien escribe código."
  exit 1
fi

exit 0
