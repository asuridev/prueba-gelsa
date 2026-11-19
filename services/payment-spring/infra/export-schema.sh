#!/usr/bin/env bash
# export-schema.sh — exporta el DDL de las entidades JPA de payment.
# Produce build/schema/baseline.sql con el dialecto real del stack, para revisarlo y
# copiarlo como src/main/resources/db/migration/V1__baseline_schema.sql.
# Requiere la infraestructura de prueba arriba (el perfil local conecta a la BD).
# Uso (desde la raíz del proyecto):
#   bash infra/up.sh && bash infra/export-schema.sh
set -u

TARGET="build/schema/baseline.sql"
TIMEOUT="${EXPORT_TIMEOUT:-180}"

if [ ! -x ./gradlew ] && [ ! -f ./gradlew ]; then
  echo "Ejecuta el script desde la raíz del proyecto (no se encontró ./gradlew)." >&2
  exit 2
fi

rm -f "$TARGET"
mkdir -p "$(dirname "$TARGET")"

echo "Exportando el esquema con PROFILE=local,schema-export…"
PROFILE=local,schema-export ./gradlew bootRun --console=plain >build/schema/export.log 2>&1 &
pid=$!

elapsed=0
while [ ! -s "$TARGET" ]; do
  if ! kill -0 "$pid" 2>/dev/null; then
    echo "La aplicación terminó sin exportar el esquema. Revisa build/schema/export.log." >&2
    exit 1
  fi
  if [ "$elapsed" -ge "$TIMEOUT" ]; then
    kill "$pid" 2>/dev/null
    echo "Timeout ($TIMEOUT s) esperando $TARGET. ¿Está la infraestructura arriba? Revisa build/schema/export.log." >&2
    exit 1
  fi
  sleep 2
  elapsed=$((elapsed + 2))
done

# El archivo aparece al construir el EntityManagerFactory; la app ya no hace falta.
sleep 2
kill "$pid" 2>/dev/null
wait "$pid" 2>/dev/null

echo "Esquema exportado en $TARGET."

if [ -f "src/main/resources/db/partial-indexes.sql" ]; then
  echo "" >> "$TARGET"
  cat "src/main/resources/db/partial-indexes.sql" >> "$TARGET"
  echo "Añadidos al DDL los índices condicionados de src/main/resources/db/partial-indexes.sql (Hibernate no los infiere)."
fi

missing=""
for constraint in uk_payments_natural uk_refunds_natural uk_wallets_natural uk_payment_methods_wallet; do
  grep -qi "$constraint" "$TARGET" || missing="$missing $constraint"
done
if [ -n "$missing" ]; then
  echo ""
  echo "AVISO: el DDL exportado no nombra estas constraints:$missing"
  echo "  Hibernate las vuelca como 'unique (...)' inline. Renómbralas en $TARGET"
  echo "  antes de copiarlo: ApiExceptionHandler traduce la violación por nombre y,"
  echo "  sin él, el error declarado del diseño se degrada a un 409 genérico."
  echo ""
fi
echo "Revísalo (constraints, índices, tipos del dialecto) y cópialo como:"
echo "  src/main/resources/db/migration/V1__baseline_schema.sql"
echo "Después, doble check estático: diff contra este archivo y contraste con las entidades y el diseño."
echo "La prueba en vivo (PROFILE=local,migrations sobre una BD sin esquema) la hace el diseñador:"
echo "  borra el volumen de la BD, que es la misma sobre la que corren los escenarios."
