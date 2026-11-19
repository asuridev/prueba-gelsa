#!/usr/bin/env bash
# check-domain-guards.sh — comprueba que el formato de los value types escalares de
# order se hace cumplir en alguna parte.
#
# build genera la clase <Tipo>Format con la regex del diseño; la LLAMADA es del agente,
# en el factory de la entidad o en el método de negocio que asigna el campo, siempre
# DESPUÉS de normalizar. Sin esa llamada el servicio acepta valores que el diseño
# declara imposibles, compila, arranca y pasa todos los escenarios que no lo miren.
#
# Es una comprobación ESTRUCTURAL: caza la ausencia, no juzga si el sitio elegido es el
# mejor. Dónde vive la llamada es del agente mientras respete la frontera hexagonal.
#
# Uso (desde la raíz del proyecto; no necesita infraestructura ni compilar):
#   bash infra/check-domain-guards.sh
#
# Código de salida:
#   0  todos los campos con formato tienen quien lo haga cumplir
#   1  hay hallazgos → vuelven al agente de código
set -u

SRC="src/main/java"

if [ ! -d "$SRC" ]; then
  echo "Ejecuta el script desde la raíz del proyecto (no se encontró $SRC)." >&2
  exit 1
fi

findings=0
detail=""

note() {  # texto
  detail="$detail  $1\n"
  findings=$((findings + 1))
}

# Un campo con formato declarado: su clase existe y alguien la llama.
guard() {  # sujeto, clase, porqué
  local subject="$1" class="$2" why="$3"
  local declaration
  declaration="$(find "$SRC" -name "$class.java" -type f 2>/dev/null | head -n 1)"
  # Autochequeo: si la clase no está, lo que falta es la generación, no el uso, y
  # reportarlo como uso ausente mandaría al agente a escribir una llamada a algo que
  # no existe.
  if [ -z "$declaration" ]; then
    note "$subject: no existe $class.java — lo genera build desde el diseño; regenera el proyecto"
    return
  fi
  # La propia declaración fuera: la clase se nombra a sí misma y contaría como su uso.
  # Y los comentarios fuera: build deja en el stub del factory un TODO que NOMBRA la
  # llamada que falta, así que mirando la prosa el gate saldría verde por su propio aviso.
  local caller=""
  local file code
  while IFS= read -r file; do
    [ -n "$file" ] || continue
    [ "$file" != "$declaration" ] || continue
    code="$(sed -e 's://.*::' -e '/^[[:space:]]*\*/d' -e '/^[[:space:]]*\/\*/d' -e '/^[[:space:]]*import /d' "$file")"
    if printf '%s' "$code" | grep -qE -- "$class[[:space:]]*\.[[:space:]]*(validate|matches)[[:space:]]*\("; then
      caller="$file"
      break
    fi
  done <<EOF
$(grep -rlF -- "$class." "$SRC" 2>/dev/null)
EOF
  [ -n "$caller" ] || note "$subject: nadie llama a $class.validate(...) — $why"
}

guard 'Order.customerEmail' 'EmailAddressFormat' 'el tipo EmailAddress declara un formato y este campo no lo hace cumplir en ninguna parte'
guard 'Order.currency' 'CurrencyCodeFormat' 'el tipo CurrencyCode declara un formato y este campo no lo hace cumplir en ninguna parte'
guard 'Product.currency' 'CurrencyCodeFormat' 'el tipo CurrencyCode declara un formato y este campo no lo hace cumplir en ninguna parte'

echo ""
echo "FORMATO DE LOS VALUE TYPES"
if [ "$findings" -eq 0 ]; then echo "  valueTypeFormat      OK"; else echo "  valueTypeFormat      KO"; fi

if [ "$findings" -gt 0 ]; then
  echo ""
  echo "HALLAZGOS ($findings)"
  printf '%b' "$detail"
  echo ""
  echo "El DTO de entrada valida el formato heredado de un value type, pero lo que no entra por la"
  echo "API (un evento, una operación interna) no pasa por él. La clase <Tipo>Format lleva la regex"
  echo "del diseño; llámala en el factory de la entidad o en el método de negocio que asigna el"
  echo "campo. Sin esa llamada el servicio acepta por esas puertas valores que el diseño declara"
  echo "imposibles, y ningún escenario que no lo mire lo delata. Ver conventions/domain-modeling.md."
  exit 1
fi

exit 0
