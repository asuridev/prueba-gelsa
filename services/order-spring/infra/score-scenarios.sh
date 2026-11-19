#!/usr/bin/env bash
# score-scenarios.sh — ejecuta las pruebas de integración de order y
# puntúa los escenarios FL-* contra specs/validation-scenarios.md.
#
# La matriz sale del XML de JUnit, sin criterio de por medio: el @DisplayName de
# cada prueba lleva el id delante de los dos puntos (FL-PRD-001-A: …). Arbitrar
# de quién es la culpa de un fallo NO es trabajo de este script — para eso está
# el agente keel-spring-validate, que se invoca solo si aquí sale algo en rojo.
#
# Uso (desde la raíz del proyecto, con la infraestructura arriba):
#   bash infra/score-scenarios.sh           # humo del arnés + suite + matriz
#   bash infra/score-scenarios.sh --score   # solo re-puntúa el XML ya existente
#
# Salida: la matriz por stdout (la de Gradle va al log). Código de salida:
#   0  todos los escenarios en OK
#   1  hay FALLO o NO_EJERCITADO → hay algo que arbitrar
#   2  precondición o arnés roto Y NADA QUE ARBITRAR: la suite no se ejecutó, o cayó
#      alguna clase entera sin dejar ni un FL-* en FALLO. Si además hay escenarios en
#      FALLO, sale 1: esos se arbitran, y devolver la corrida al agente de pruebas
#      cuando el rojo es del servidor es un bucle que no converge.
#   3  ENTORNO bloqueado: otro proceso sostiene este directorio. No es un defecto de
#      nadie y no hay agente que relanzar — se resuelve y se reintenta.
#
# El 1 y el 2 se reparten por lo que hay que HACER, no por dónde está el defecto.
# Arbitrar es contrastar un `Then` con su evidencia, así que sin ningún FL-* en FALLO,
# OMITIDO o NO_EJERCITADO no hay ningún `Then` que leer: el 1 mandaría al árbitro a
# opinar sobre un conjunto vacío, y su veredicto no puede relanzar a nadie. Por eso el
# 1 EXIGE que la matriz tenga algo, y todo lo demás vuelve al agente de pruebas.
#
# Y este script limpia sus propios workers al salir (ver kill_workers), así que el 3
# debería ser raro: la causa se corta donde se produce y no en la corrida siguiente.
#   bash infra/score-scenarios.sh --kill-workers   # solo esa limpieza, sin ejecutar nada
#
# El 3 existe porque su síntoma se disfraza del 2. Una corrida anterior interrumpida
# (un timeout de la herramienta que la lanzó) deja vivos el proceso de Gradle y su
# Test Executor, que siguen sosteniendo un lock sobre build/. El siguiente intento
# muere al limpiar los resultados, y leído como "HARNESS: KO" manda a revisar un
# andamiaje que está perfectamente bien —o a relanzar al agente de pruebas, que no
# tiene nada que arreglar—. Distinguirlo cuesta una comprobación y ahorra el ciclo.
set -u

RESULTS="build/test-results/integrationTest"
LOG_DIR="build/keel-scenarios"
LOG="$LOG_DIR/run.log"
EVIDENCE="build/keel-failures"
SCENARIOS="specs/validation-scenarios.md"

if [ ! -f ./gradlew ]; then
  echo "Ejecuta el script desde la raíz del proyecto (no se encontró ./gradlew)." >&2
  exit 2
fi

mkdir -p "$LOG_DIR"

# La evidencia de la corrida ANTERIOR se borra antes de empezar, y no es limpieza: un
# volcado que sobrevive a una corrida verde se lee igual que uno recién escrito. La matriz
# solo cita la ruta de los fallos de ESTA pasada, así que un volcado de hace tres
# horas junto a un 100% no informa de nada — engaña, que es peor. Mismo motivo por el que el
# log de Gradle se sobrescribe en vez de acumularse.
rm -rf "$EVIDENCE"

PROJECT_DIR="$(pwd -P)"

# Las formas en que este directorio puede aparecer escrito en la línea de comando de un
# proceso. Son DOS porque en Windows el java de Gradle es un proceso NATIVO: su argv lleva
# C:\\Users\\... aunque este script se vea a sí mismo en /c/Users/..., y buscar una sola
# forma no encuentra nada justo en el sistema donde el defecto muerde.
project_path_forms() {
  printf '%s\n' "$PROJECT_DIR"
  if command -v cygpath >/dev/null 2>&1; then
    cygpath -w "$PROJECT_DIR" 2>/dev/null || true
  fi
}

# Todos los procesos, como "<pid> <línea de comando>". Dos frentes y ninguno sobra: el ps de
# Git Bash no ve los procesos nativos de Windows, y Get-CimInstance no existe fuera de ahí.
# Si no responde ninguno se dice en voz alta: no encontrar workers porque no se pudo MIRAR se
# lee igual que no haberlos, y esa confusión es la que cuesta la corrida siguiente.
#
# Cuesta ~4 s en Windows, y es arrancar PowerShell: acotar la consulta a java.exe no lo baja
# (medido) y además dejaría las dos ramas mirando cosas distintas. Se paga una vez por
# invocación de un script que dura minutos.
list_processes() {
  salida=""
  if command -v powershell.exe >/dev/null 2>&1; then
    salida="$(powershell.exe -NoProfile -NonInteractive -Command 'Get-CimInstance Win32_Process | ForEach-Object { [string]$_.ProcessId + " " + [string]$_.CommandLine }' 2>/dev/null || true)"
  fi
  [ -n "$salida" ] || salida="$(ps ax -o pid=,args= 2>/dev/null || true)"
  [ -n "$salida" ] || salida="$(ps -ef 2>/dev/null || true)"
  if [ -z "$salida" ]; then
    echo "AVISO: no se pudo listar procesos (ni powershell ni ps): los workers de Gradle de" >&2
    echo "  esta corrida quedan sin limpiar. Si la siguiente sale con 3, la causa es esta." >&2
    return 1
  fi
  printf '%s\n' "$salida"
}

# Los workers de Gradle —los "Gradle Test Executor"— son JVMs APARTE del daemon: no caen con
# ./gradlew --stop y jps NO los lista. Lo único que los identifica es la propiedad
# -Dorg.gradle.internal.worker.tmpdir=<build de este proyecto> de su línea de comando, y de
# ahí sale también el alcance: se terminan SOLO los de este directorio. El daemon no se toca
# (es reutilizable y no sostiene el lock) y los de otro proyecto tampoco.
worker_pids() {
  procesos="$(list_processes)" || return 0
  candidatos="$(printf '%s\n' "$procesos" | grep -F 'org.gradle.internal.worker.tmpdir' || true)"
  [ -n "$candidatos" ] || return 0
  project_path_forms | while IFS= read -r ruta; do
    [ -n "$ruta" ] || continue
    printf '%s\n' "$candidatos" | grep -F "$ruta" || true
  done | awk '{ print $1 }' | grep -E '^[0-9]+$' | sort -u || true
}

# Se invoca al SALIR, pase lo que pase. Un worker que sobrevive a esta corrida sostiene el
# lock de build/test-results/integrationTest/binary/output.bin y envenena TODA invocación
# posterior: el síntoma no aparece en la corrida que lo causa sino en la siguiente, que es lo
# que lo hace caro de diagnosticar. Cortarlo donde se produce cuesta esta función.
kill_workers() {
  pids="$(worker_pids)"
  [ -n "$pids" ] || return 0
  echo "" >&2
  echo "Limpieza: quedan workers de Gradle de este directorio; se terminan." >&2
  for pid in $pids; do
    echo "  pid $pid" >&2
    kill "$pid" 2>/dev/null || true
  done
  sleep 2
  for pid in $(worker_pids); do
    kill -9 "$pid" 2>/dev/null || true
  done
}

trap kill_workers EXIT

if [ "${1:-}" = "--kill-workers" ]; then
  # Modo suelto: es lo que manda ejecutar el mensaje de un entorno bloqueado. No arranca nada.
  kill_workers
  echo "Workers de Gradle de este directorio: terminados (si quedaba alguno)."
  exit 0
fi

# ¿Lo que tumbó a Gradle fue un lock y no un defecto? Las cuatro formas en que se
# manifiesta el mismo hecho: el lock propio de Gradle, y el del sistema de archivos
# en sus dos dialectos (POSIX y Windows). Se mira el LOG y no el código de salida
# porque Gradle devuelve 1 para esto igual que para una compilación rota.
blocked_by_lock() {
  grep -qiE "Timeout waiting to lock|Device or resource busy|being used by another process|Could not delete|Unable to delete" "$LOG" 2>/dev/null
}

# El remedio se escribe UNA vez. Estuvo duplicado entre este mensaje y el del rm fallido, y
# dos copias del mismo remedio divergen a la primera: la que se queda atrás es la que alguien
# lee. Y lo que decían —listar con jps y filtrar por gradle— no funciona: jps NO los ve.
print_remedy() {
  echo ""
  echo "  Para resolverlo:"
  echo "    bash infra/score-scenarios.sh --kill-workers   # los workers de ESTE directorio"
  echo "    ./gradlew --stop                               # y los daemons, si sigue"
  echo ""
  echo "  Los workers son JVMs aparte: --stop no se las lleva y jps no las lista. Lo único"
  echo "  que las identifica es -Dorg.gradle.internal.worker.tmpdir apuntando aquí."
}

report_locked() {  # $1 = qué paso se quedó bloqueado
  echo ""
  echo "ENTORNO: $1 no pudo continuar — otro proceso tiene bloqueado este directorio."
  echo "  La suite NO se ejecutó, y esto NO es un defecto del arnés ni del código."
  print_remedy
  echo ""
  echo "  log: $LOG"
  exit 3
}

# El snapshot del diseño tiene que ser el que build escribió. `specs.sha256` es su sello: una
# línea «<sha256>  <ruta>» por archivo de specs/, calculada SIN retornos de carro para que un
# checkout con autocrlf no la rompa. Si no casa, alguien editó el diseño desde aquí, y un
# escenario corregido por quien tiene la suite delante deja de medir lo que el diseño dijo:
# eso es un culpable 'design' que se PROPONE (design-gaps.yaml), no se aplica. En la corrida
# `catalog` (2026-09-21) se aplicó, la suite cerró en verde y el hueco no volvió al método.
if [ -f "specs.sha256" ]; then
  if command -v sha256sum >/dev/null 2>&1; then sha_cmd="sha256sum"; else sha_cmd="shasum -a 256"; fi
  seal_broken=""
  while read -r expected file; do
    [ -n "$file" ] || continue
    if [ ! -f "$file" ]; then seal_broken="$seal_broken $file(falta)"; continue; fi
    actual="$(tr -d '\r' < "$file" | $sha_cmd | cut -d' ' -f1)"
    [ "$actual" = "$expected" ] || seal_broken="$seal_broken $file"
  done < "specs.sha256"
  if [ -n "$seal_broken" ]; then
    echo "DISEÑO: el snapshot specs/ no es el que escribió keel-spring build:$seal_broken"
    echo "  La suite NO se ejecuta contra un diseño editado desde el proyecto generado."
    echo "  Si un escenario contradice al diseño, es un hueco del DISEÑO: va a design-gaps.yaml y al"
    echo "  informe, y lo corrige el diseñador en el workspace re-ejecutando keel-spring build."
    echo "  Para deshacer la edición: git checkout -- specs/"
    exit 2
  fi
fi

score_only=0
[ "${1:-}" = "--score" ] && score_only=1
# Con --score la suite no se ejecuta aquí, así que no hay veredicto de Gradle que
# contradiga a la matriz: 0 es el valor correcto para ese modo.
suite_failed=0

if [ "$score_only" -eq 0 ]; then
  # Limpiar los resultados es el primer paso Y el primer detector: si el directorio
  # sigue ahí después del rm, no es un permiso —el script acaba de crear su propio
  # log al lado— sino un lock, y el único que lo sostiene es un Gradle que no murió.
  rm -rf "$RESULTS" 2>/dev/null
  if [ -d "$RESULTS" ]; then
    echo ""
    echo "ENTORNO: no se pudo limpiar $RESULTS — otro proceso lo tiene abierto."
    echo "  La suite NO se ejecutó, y esto NO es un defecto del arnés ni del código:"
    echo "  casi siempre es una corrida anterior que se interrumpió sin terminar y dejó"
    echo "  vivos su proceso de Gradle y su Test Executor."
    print_remedy
    echo ""
    echo "  Cuando no quede ninguno, vuelve a lanzar este script tal cual."
    exit 3
  fi
  # Jar ejecutable para la segunda réplica (escenarios de clúster).
  echo "Empaquetando el jar (bootJar)..."
  if ! ./gradlew bootJar --console=plain >"$LOG" 2>&1; then
    blocked_by_lock && report_locked "el empaquetado del jar"
    echo ""
    echo "HARNESS: KO - bootJar falló, la suite NO se ejecutó."
    echo "  log: $LOG"
    exit 2
  fi

  # Humo del arnés primero: son segundos y comprueba la fontanería de la que
  # dependen TODAS las clases de flujo (reset, servidor vivo, credenciales,
  # canales, caché). En rojo no se ejecuta la suite: correrla sobre una
  # fontanería rota produce decenas de fallos que parecen de negocio y no lo
  # son, y cuesta una pasada entera descubrirlo.
  echo "Humo del arnés (HarnessSmokeIT)…"
  if ! ./gradlew integrationTest --tests '*HarnessSmokeIT' --console=plain >"$LOG" 2>&1; then
    # El lock se descarta ANTES de acusar al andamiaje: los dos matan el humo del
    # arnés en el mismo sitio, y solo uno de los dos tiene a quien relanzar.
    blocked_by_lock && report_locked "el humo del arnés"
    echo ""
    echo "HARNESS: KO — la suite NO se ejecutó."
    echo "  El defecto está en el andamiaje que generó build (AbstractFlowIT,"
    echo "  FailureCapture, HarnessSmokeIT) o falta infraestructura."
    echo "  log: $LOG"
    exit 2
  fi
  echo "Humo del arnés: OK."
  echo "Ejecutando la suite completa…"
  # El código de salida de Gradle se GUARDA, no se ignora. La matriz solo mira los
  # `FL-*`, así que una prueba en rojo que no sea un escenario —un caso borde que el
  # agente añadió por su cuenta— no aparecería en ninguna fila y el script diría
  # "100%" sobre una suite roja. Ver el cierre.
  ./gradlew integrationTest --console=plain >>"$LOG" 2>&1 || suite_failed=1
  # Y si lo que la tumbó a mitad fue el lock, la matriz que saldría de un XML
  # incompleto sería todo NO_EJERCITADO: un rojo que mandaría a arbitrar un fallo
  # que no existe.
  [ "$suite_failed" -eq 1 ] && blocked_by_lock && report_locked "la suite"
fi

if [ ! -d "$RESULTS" ]; then
  echo ""
  echo "No hay resultados en $RESULTS: la suite no llegó a ejecutarse."
  echo "  log: $LOG"
  exit 2
fi

# Matriz desde el XML de JUnit. Un <testcase> cuenta como FALLO si lleva dentro
# un <failure> o un <error>; el id es lo que va delante de los dos puntos del
# @DisplayName y la clase sale del atributo classname (sin el paquete).
matrix="$(awk '
  BEGIN { RS = "<testcase " }
  NR == 1 { next }
  {
    rec = $0
    close_tag = index(rec, "</testcase>")
    self_tag = index(rec, "/>")
    if (close_tag > 0 && (self_tag == 0 || close_tag < self_tag)) seg = substr(rec, 1, close_tag)
    else if (self_tag > 0) seg = substr(rec, 1, self_tag)
    else seg = rec

    name = ""; cls = ""
    # El atributo EXACTO: un /name=/ a secas casa dentro de classname="…", y el orden de los
    # dos lo decide el runner (Gradle escribe name primero; Vitest y gotestsum, classname).
    if (match(" " seg, /[ \t\n]name="[^"]*"/)) name = substr(" " seg, RSTART + 7, RLENGTH - 8)
    if (match(seg, /classname="[^"]*"/)) cls = substr(seg, RSTART + 11, RLENGTH - 12)
    if (name == "") next

    # Sin classname no es un test: es el nodo contenedor que algunos runners
    # emiten para la clase. Su @DisplayName suele empezar por el id del flujo, y
    # colarlo duplica la fila y falsea el recuento (9 escenarios donde hay 6).
    if (cls == "") next

    n++
    failed = (seg ~ /<(failure|error)[ >]/)
    key[n] = cls SUBSEP name
    bad[n] = failed
    rest = name; prefix = ""
    while ((cut = index(rest, "/")) > 0) {
      prefix = prefix substr(rest, 1, cut - 1)
      nested[cls SUBSEP prefix] = 1
      if (failed) nested_bad[cls SUBSEP prefix] = 1
      prefix = prefix "/"
      rest = substr(rest, cut + 1)
    }
    id = name
    if (index(id, ":") > 0) id = substr(id, 1, index(id, ":") - 1)
    sub(/^.*\//, "", id)
    gsub(/^[ \t]+|[ \t]+$/, "", id)
    if (id ~ /^TestFL_[A-Za-z0-9_]+$/) { id = substr(id, 5); gsub(/_/, "-", id) }
    # El id es un token, nunca una frase: un @DisplayName de clase como
    # "FL-RES-001 · alta de reserva" no es un escenario.
    if (id !~ /^FL-[A-Za-z0-9-]+$/) next

    sub(/^.*\./, "", cls)
    if (failed) row[n] = "FALLO\t" id "\t" cls
    else if (seg ~ /<skipped[ \/>]/) row[n] = "OMITIDO\t" id "\t" cls
    else row[n] = "OK\t" id "\t" cls
  }
  END {
    for (i = 1; i <= n; i++) {
      if (!(i in row)) continue
      if ((key[i] in nested) && !(bad[i] && !(key[i] in nested_bad))) continue
      print row[i]
    }
  }
' "$RESULTS"/*.xml 2>/dev/null | sort -k2,2)"

# Escenarios que el documento declara y ninguna clase ejercita. No son OK: son
# cobertura que falta. El cruce es por prefijo porque el documento numera el
# flujo (FL-PRD-001) y las pruebas numeran cada escenario dentro de él
# (FL-PRD-001-A). Y al revés: una prueba con un id FL-* que el documento no
# declara no puntúa (keel-core/gen/junit-scoring, el mismo cruce que en keel-nest).
uncovered=""
foreign=""
foreign_rows=""
foreign_failures=""
if [ -f "$SCENARIOS" ]; then
  declared="$(grep -oE '^#{1,6}[[:space:]]*FL-[A-Za-z0-9-]+' "$SCENARIOS" \
              | grep -oE 'FL-[A-Za-z0-9-]+' | sort -u)"
  covered="$(printf '%s\n' "$matrix" | cut -f2 | sort -u)"
  for id in $declared; do
    printf '%s\n' "$covered" | grep -qE "^${id}(-|$)" || uncovered="$uncovered $id"
  done
  for id in $covered; do
    owned=0
    for declared_id in $declared; do
      case "$id" in "$declared_id"|"$declared_id"-*) owned=1; break ;; esac
    done
    [ "$owned" -eq 1 ] || foreign="$foreign $id"
  done
  if [ -n "$foreign" ]; then
    foreign_rows="$(printf '%s\n' "$matrix" | awk -F '\t' -v ids="$foreign" \
      'BEGIN { n = split(ids, list, " "); for (i = 1; i <= n; i++) skip[list[i]] = 1 } ($2 in skip)')"
    matrix="$(printf '%s\n' "$matrix" | awk -F '\t' -v ids="$foreign" \
      'BEGIN { n = split(ids, list, " "); for (i = 1; i <= n; i++) skip[list[i]] = 1 } NF > 0 && !($2 in skip)')"
    foreign_failures="$(printf '%s\n' "$foreign_rows" | awk -F '\t' \
      '$1 == "FALLO" { printf "    %s  (%s)\n      el documento de escenarios no declara %s\n", $2, $3, $2 }')"
  fi
fi

echo ""
echo "MATRIZ"
printf '%s\n' "$matrix" | while IFS="$(printf '\t')" read -r result id cls; do
  [ -n "${id:-}" ] || continue
  if [ "$result" = "FALLO" ]; then
    printf '  %-8s %-20s %-28s %s\n' "$result" "$id" "$cls" "$EVIDENCE/$id.json"
  else
    printf '  %-8s %-20s %s\n' "$result" "$id" "$cls"
  fi
done
for id in $uncovered; do
  printf '  %-8s %s\n' "NO_EJERC" "$id"
done
if [ -n "$foreign" ]; then
  printf '%s\n' "$foreign_rows" | while IFS="$(printf '\t')" read -r result id cls; do
    [ -n "${id:-}" ] || continue
    printf '  %-8s %-20s %-28s %s\n' "AJENO" "$id" "$cls" "($result: el documento no lo declara; no puntúa)"
  done
fi

ok=$(printf '%s\n' "$matrix" | grep -c '^OK')
ko=$(printf '%s\n' "$matrix" | grep -c '^FALLO')
sk=$(printf '%s\n' "$matrix" | grep -c '^OMITIDO')
nc=0
for id in $uncovered; do nc=$((nc + 1)); done

# Las pruebas en rojo que NO son escenarios: la matriz solo conoce los `FL-*`, y un
# testcase con otro nombre no pasa por ninguna fila. El caso que más duele es el
# `initializationError` que JUnit sintetiza cuando revienta un @BeforeAll: la clase
# entera no llega a ejecutar ni un escenario, así que sus `FL-*` no aparecen en el XML
# y reaparecen abajo como NO_EJERCITADO — una etiqueta que dice "sin cobertura" cuando
# lo que hubo fue un rojo. Por eso esto es una función y se invoca en LOS DOS
# desenlaces: enterrar la causa raíz en $LOG justo cuando hay algo que enmascarar es
# exactamente lo que hacía la versión anterior, que solo la imprimía con la matriz limpia.
non_scenario_failures() {
  awk '
    BEGIN { RS = "<testcase " }
    NR == 1 { next }
    {
      rec = $0
      close_tag = index(rec, "</testcase>")
      self_tag = index(rec, "/>")
      if (close_tag > 0 && (self_tag == 0 || close_tag < self_tag)) seg = substr(rec, 1, close_tag)
      else if (self_tag > 0) seg = substr(rec, 1, self_tag)
      else seg = rec
      name = ""; cls = ""
      if (match(" " seg, /[ \t\n]name="[^"]*"/)) name = substr(" " seg, RSTART + 7, RLENGTH - 8)
      if (match(seg, /classname="[^"]*"/)) cls = substr(seg, RSTART + 11, RLENGTH - 12)
      if (name == "" || cls == "") next
      n++
      failed = (seg ~ /<(failure|error)[ >]/)
      key[n] = cls SUBSEP name
      bad[n] = failed
      rest = name; prefix = ""
      while ((cut = index(rest, "/")) > 0) {
        prefix = prefix substr(rest, 1, cut - 1)
        nested[cls SUBSEP prefix] = 1
        if (failed) nested_bad[cls SUBSEP prefix] = 1
        prefix = prefix "/"
        rest = substr(rest, cut + 1)
      }
      if (!failed) next
      id = name
      if (index(id, ":") > 0) id = substr(id, 1, index(id, ":") - 1)
      sub(/^.*\//, "", id)
      gsub(/^[ \t]+|[ \t]+$/, "", id)
      if (id ~ /^TestFL_[A-Za-z0-9_]+$/) { id = substr(id, 5); gsub(/_/, "-", id) }
      if (id ~ /^FL-[A-Za-z0-9-]+$/) next
      sub(/.*\./, "", cls)
      # El MENSAJE, que es lo único que convierte esta línea en accionable. Estaba aquí desde
      # siempre —el XML de JUnit lo guarda entero— y se descartaba: por stdout salía solo la clase de la
      # excepción y la línea donde saltó, y a diagnosticarlo se le iba un ciclo completo. Se colapsa a
      # una línea y se acota: quien lee esto es la sesión más larga del pipeline.
      msg = ""
      if (match(seg, /<(failure|error)[^>]*message="[^"]*"/)) {
        frag = substr(seg, RSTART, RLENGTH)
        if (match(frag, /message="[^"]*"/)) msg = substr(frag, RSTART + 9, RLENGTH - 10)
      }
      # gotestsum pone siempre message="Failed": lo que dijo la prueba va en el CUERPO, entre las
      # líneas de go test (=== RUN, --- FAIL, y las de los subtests).
      if (msg == "Failed" && match(seg, /<(failure|error)[^>]*>/)) {
        body = substr(seg, RSTART + RLENGTH)
        if (index(body, "</") > 0) body = substr(body, 1, index(body, "</") - 1)
        lines = split(body, part, /&#[xX][aA];|&#10;/)
        msg = ""
        for (k = 1; k <= lines; k++) {
          line = part[k]
          if (line ~ /^(=== |--- )/) continue
          sub(/^([ \t]|&#[xX]9;|&#9;)+/, "", line)
          if (line == "") continue
          msg = (msg == "" ? line : msg " " line)
        }
      }
      # El salto de línea en decimal (Gradle, Vitest) o en hexadecimal (xUnit v3, gotestsum), y la comilla
      # que xUnit escapa además con una barra (\&quot;): antes que &quot; a secas, o la barra se queda.
      # gotestsum escribe las comillas y el tabulador en numérico.
      gsub(/&#10;/, " ", msg); gsub(/&#13;/, " ", msg)
      gsub(/&#[xX][aA];/, " ", msg); gsub(/&#[xX][dD];/, " ", msg)
      gsub(/&#[xX]9;/, " ", msg); gsub(/&#9;/, " ", msg)
      gsub(/\\&quot;/, "\"", msg)
      gsub(/&quot;/, "\"", msg); gsub(/&#34;/, "\"", msg); gsub(/&#39;/, "\047", msg)
      gsub(/&lt;/, "<", msg); gsub(/&gt;/, ">", msg)
      gsub(/&amp;/, "\\&", msg)
      gsub(/  +/, " ", msg)
      if (length(msg) > 400) msg = substr(msg, 1, 400) " [...]"
      row[n] = sprintf("    %s  (%s)", name, cls)
      if (msg != "") row[n] = row[n] "\n      " msg
    }
    END {
      for (i = 1; i <= n; i++) {
        if (!(i in row)) continue
        if ((key[i] in nested) && !(bad[i] && !(key[i] in nested_bad))) continue
        print row[i]
      }
    }
  ' "$RESULTS"/*.xml 2>/dev/null
}

broken="$(non_scenario_failures)"
# Las pruebas en rojo con un id FL-* que el documento no declara tampoco salen en la matriz.
if [ -n "$foreign_failures" ]; then broken="$(printf '%s\n%s' "$broken" "$foreign_failures")"; fi

echo ""
if [ "$ko" -eq 0 ] && [ "$sk" -eq 0 ] && [ "$nc" -eq 0 ] && [ "$ok" -gt 0 ]; then
  # La matriz está limpia. Antes de cantar el 100% hay que preguntarle a Gradle: decir
  # "100%" con la suite roja es peor que no tener gate — el pipeline avanzaría a la fase
  # siguiente creyendo el servicio verde.
  if [ "$suite_failed" -ne 0 ]; then
    echo "RESULTADO: KO — los $ok escenario(s) FL-* están en OK, pero la suite falló."
    echo "  Hay pruebas en rojo que NO son escenarios y por eso no salen en la matriz:"
    printf '%s\n' "$broken"
    echo "  log completo de Gradle: $LOG"
    echo "  Son del agente de pruebas, no del diseño: o las arregla o las retira."
    echo "  Sale con 2 y NO con 1: no hay ningún FL-* en FALLO, así que no hay ningún Then"
    echo "  que arbitrar — el árbitro opinaría sobre un conjunto vacío y no relanza a nadie."
    exit 2
  fi
  echo "RESULTADO: OK — $ok escenario(s) al 100%."
  exit 0
fi

echo "RESULTADO: KO — $ok OK · $ko FALLO · $sk omitido(s) · $nc no ejercitado(s)."
echo "  evidencia por fallo: $EVIDENCE/<FL-id>.json (request, response y aserción)"
echo "  log completo de Gradle: $LOG"
if [ -n "$broken" ]; then
  echo ""
  echo "  ARNÉS: hay pruebas en rojo que NO son escenarios y no salen en la matriz."
  printf '%s\n' "$broken"
  echo "  Un \`initializationError\` aquí significa que esa clase no ejecutó NINGÚN escenario:"
  echo "  los FL-* que le tocaban salen arriba como NO_EJERC, y no es falta de cobertura."
  echo "  La causa va en la línea de debajo de cada clase; el volcado, en"
  echo "  $EVIDENCE/<Clase>-init.json (con el último comando ejecutado y su salida)."
  if [ "$ko" -eq 0 ]; then
    echo "  No hay ningún escenario en FALLO: no hay nada que arbitrar, así que esto sale"
    echo "  con 2 (arnés roto) y no con 1: se arregla la clase y se vuelve a puntuar."
    exit 2
  fi
  echo "  PERO hay $ko escenario(s) FL-* en FALLO, y eso SÍ se arbitra: esto sale con 1."
  echo "  Arreglar el @BeforeAll NO los pone en verde. Una inicialización que revienta porque"
  echo "  esperaba un comportamiento del servidor (un correo que no llega, un estado que no"
  echo "  transita) es el MISMO rojo que los FL-* de arriba, visto antes: no es del arnés."
  echo "  Salir con 2 aquí devolvería la corrida al agente de pruebas, que no puede leer"
  echo "  src/main/java — es exactamente el bucle que esta condición cierra."
fi

# La matriz VACÍA no es un rojo que arbitrar. Aquí solo se llega con ok=0 —con ok>0 y nada
# en rojo manda el desenlace verde de arriba—, o sea: la suite corrió y no ejercitó ni un
# FL-*. Eso es el arnés y no el diseño, y salir con 1 mandaba al árbitro a leer una matriz
# sin filas: el único desenlace del que no se sale, porque su veredicto no relanza a nadie.
if [ "$ko" -eq 0 ] && [ "$sk" -eq 0 ] && [ "$nc" -eq 0 ]; then
  echo ""
  echo "  ARNÉS: la matriz está VACÍA — la suite no ejercitó ni un escenario FL-*."
  echo "  No hay nada que arbitrar: sale con 2 y vuelve al agente de pruebas."
  exit 2
fi
exit 1
