#!/usr/bin/env bash
# check-logging.sh — higiene de los logs de payment.
#
# Los logs de las fronteras los genera build; los de negocio los escribe el agente siguiendo
# docs/keel/conventions/logging.md. Este gate veta cuatro formas que no rompen nada y salen
# caras: concatenar dentro del log, pasar el objeto de entrada entero (su toString() lleva los
# datos de quien llama al backend de logs), un ERROR desde domain/ o application/ (ahí un fallo
# se lanza y lo registra la frontera) y un executor que no propaga el contexto.
#
# Heurístico: el objeto entero se reconoce por el nombre de la variable (command, dto,
# request, payload, body, message...). Loguear un campo —command.orderId()— es lo correcto y
# no casa.
#
# Uso (desde la raíz del proyecto; no necesita infraestructura ni compilar):
#   bash infra/check-logging.sh
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

# Mira solo código vivo: sin comentarios de línea ni de bloque, que pueden citar la forma
# prohibida para explicarla.
scan() {  # regla, patrón, porqué
  local rule="$1" pattern="$2" why="$3"
  local file hits
  while IFS= read -r file; do
    [ -n "$file" ] || continue
    hits="$(sed -e 's://.*::' -e '/^[[:space:]]*\*/d' -e '/^[[:space:]]*\/\*/d' "$file" | grep -nE -- "$pattern" || true)"
    if [ -n "$hits" ]; then
      findings=$((findings + 1))
      detail="$detail  [$rule] ${file#./}: $why\n$(printf '%s\n' "$hits" | sed 's/^/      /')\n"
    fi
  done <<EOF
$(grep -rlE -- '(log|LOG|logger|LOGGER)[.](trace|debug|info|warn|error)[[:space:]]*[(]' "$SRC" 2>/dev/null)
EOF
}

scan 'concat' '(log|LOG|logger|LOGGER)[.](trace|debug|info|warn|error)[[:space:]]*[(][^;]*("[[:space:]]*[+]|[+][[:space:]]*")' 'concatena dentro del log: usa parámetros {} (o addKeyValue) para que los valores salgan como campos y no se construya la cadena con el nivel apagado'
scan 'wholeObject' '(log|LOG|logger|LOGGER)[.](trace|debug|info|warn|error)[[:space:]]*[(][^;]*,[[:space:]]*(command|query|dto|request|payload|body|message)[[:space:]]*[,)]' 'loguea un objeto de entrada entero: su toString() lleva al backend de logs los datos de quien llama; loguea solo ids o campos concretos'
# Un dato PERSONAL del diseño (DSL 2.28: card, customerRef, gatewayMethodRef, reason): ningún log lo lleva. Loguea el id del agregado.
scan 'personal' '(log|LOG|logger|LOGGER)[.](trace|debug|info|warn|error)[[:space:]]*[(][^;]*[.](card|getCard|customerRef|getCustomerRef|gatewayMethodRef|getGatewayMethodRef|reason|getReason)[[:space:]]*[(][[:space:]]*[)]' 'loguea un campo que el diseño marca personal (DSL 2.28): su valor no puede acabar en el backend de logs; loguea el id del agregado'

# Un ERROR desde el dominio o los casos de uso. Ahí un fallo se LANZA como excepción: la frontera
# (UseCaseMediator y el adaptador de entrada) lo registra una vez, con su nivel y su pila. Un
# log.error además duplica la línea y, peor, suele acompañar a un fallo que se traga —se loguea y
# se sigue—, que es justo lo que el diseño no puede ver. Solo en esas dos capas: en
# infrastructure un ERROR es legítimo (un relay que abandona un evento, un adaptador).
while IFS= read -r file; do
  [ -n "$file" ] || continue
  case "$file" in */domain/*|*/application/*) ;; *) continue ;; esac
  hits="$(sed -e 's://.*::' -e '/^[[:space:]]*\*/d' -e '/^[[:space:]]*\/\*/d' "$file" | grep -nE -- '(log|LOG|logger|LOGGER)[.](error[[:space:]]*[(]|atError[[:space:]]*[(])' || true)"
  if [ -n "$hits" ]; then
    findings=$((findings + 1))
    detail="$detail  [errorLevel] ${file#./}: ERROR desde dominio o casos de uso: lanza la excepción y deja que la frontera la registre (una vez, con su pila); si es una degradación elegida, WARN\n$(printf '%s\n' "$hits" | sed 's/^/      /')\n"
  fi
done <<EOF
$(grep -rlE -- '[.](error|atError)[[:space:]]*[(]' "$SRC" 2>/dev/null)
EOF

# Trabajo lanzado a otro hilo sin propagar el contexto: las tareas escriben logs sin correlationId
# y, con telemetría, abren spans huérfanos, separados de la traza de la petición. Con hilos
# virtuales lanzar trabajo es barato, así que se vetan TODAS las formas de hacerlo a pelo, no solo
# el executor de hilos virtuales. El único sitio donde puede aparecer es el propio helper.
while IFS= read -r file; do
  [ -n "$file" ] || continue
  case "$file" in */ContextPropagatingExecutors.java) continue ;; esac
  hits="$(sed -e 's://.*::' -e '/^[[:space:]]*\*/d' -e '/^[[:space:]]*\/\*/d' "$file" | grep -nE -- '(^|[^A-Za-z0-9_])Executors[.]new(VirtualThreadPerTaskExecutor|ThreadPerTaskExecutor|FixedThreadPool|CachedThreadPool|SingleThreadExecutor|WorkStealingPool|ScheduledThreadPool|SingleThreadScheduledExecutor)[[:space:]]*[(]' || true)"
  if [ -n "$hits" ]; then
    findings=$((findings + 1))
    detail="$detail  [context] ${file#./}: crea un executor que no propaga el contexto (MDC, traza y correlación): usa ContextPropagatingExecutors.newVirtualThreadPerTaskExecutor()\n$(printf '%s\n' "$hits" | sed 's/^/      /')\n"
  fi
done <<EOF
$(grep -rlE -- '(^|[^A-Za-z0-9_])Executors[.]new(VirtualThreadPerTaskExecutor|ThreadPerTaskExecutor|FixedThreadPool|CachedThreadPool|SingleThreadExecutor|WorkStealingPool|ScheduledThreadPool|SingleThreadScheduledExecutor)[[:space:]]*[(]' "$SRC" 2>/dev/null)
EOF

while IFS= read -r file; do
  [ -n "$file" ] || continue
  case "$file" in */ContextPropagatingExecutors.java) continue ;; esac
  hits="$(sed -e 's://.*::' -e '/^[[:space:]]*\*/d' -e '/^[[:space:]]*\/\*/d' "$file" | grep -nE -- '(Thread[.](ofVirtual|ofPlatform|startVirtualThread)[[:space:]]*[(]|new[[:space:]]+Thread[[:space:]]*[(])' || true)"
  if [ -n "$hits" ]; then
    findings=$((findings + 1))
    detail="$detail  [context] ${file#./}: lanza un hilo a mano, que empieza sin contexto: usa ContextPropagatingExecutors.newVirtualThreadPerTaskExecutor() y submit()\n$(printf '%s\n' "$hits" | sed 's/^/      /')\n"
  fi
done <<EOF
$(grep -rlE -- '(Thread[.](ofVirtual|ofPlatform|startVirtualThread)[[:space:]]*[(]|new[[:space:]]+Thread[[:space:]]*[(])' "$SRC" 2>/dev/null)
EOF

while IFS= read -r file; do
  [ -n "$file" ] || continue
  case "$file" in */ContextPropagatingExecutors.java) continue ;; esac
  hits="$(sed -e 's://.*::' -e '/^[[:space:]]*\*/d' -e '/^[[:space:]]*\/\*/d' "$file" | grep -nE -- 'CompletableFuture[.](supplyAsync|runAsync)[[:space:]]*[(]' || true)"
  if [ -n "$hits" ]; then
    findings=$((findings + 1))
    detail="$detail  [context] ${file#./}: CompletableFuture sin executor corre en el pool común de Java, sin contexto: usa ContextPropagatingExecutors.newVirtualThreadPerTaskExecutor() y submit()\n$(printf '%s\n' "$hits" | sed 's/^/      /')\n"
  fi
done <<EOF
$(grep -rlE -- 'CompletableFuture[.](supplyAsync|runAsync)[[:space:]]*[(]' "$SRC" 2>/dev/null)
EOF

while IFS= read -r file; do
  [ -n "$file" ] || continue
  case "$file" in */ContextPropagatingExecutors.java) continue ;; esac
  hits="$(sed -e 's://.*::' -e '/^[[:space:]]*\*/d' -e '/^[[:space:]]*\/\*/d' "$file" | grep -nE -- '[.](parallelStream|parallel)[[:space:]]*[(][[:space:]]*[)]' || true)"
  if [ -n "$hits" ]; then
    findings=$((findings + 1))
    detail="$detail  [context] ${file#./}: un stream paralelo reparte el trabajo en el pool común de Java, sin contexto: usa un stream secuencial o el executor del proyecto\n$(printf '%s\n' "$hits" | sed 's/^/      /')\n"
  fi
done <<EOF
$(grep -rlE -- '[.](parallelStream|parallel)[[:space:]]*[(][[:space:]]*[)]' "$SRC" 2>/dev/null)
EOF

echo ""
echo "HIGIENE DE LOGS Y CONTEXTO"
if [ "$findings" -eq 0 ]; then echo "  logging              OK"; else echo "  logging              KO"; fi

if [ "$findings" -gt 0 ]; then
  echo ""
  echo "HALLAZGOS ($findings)"
  printf '%b' "$detail"
  echo ""
  echo "Ver docs/keel/conventions/logging.md: qué loguea ya build, qué puede añadir el agente y"
  echo "qué no se loguea nunca."
  exit 1
fi

exit 0
