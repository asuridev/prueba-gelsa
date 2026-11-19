# Datos personales de order

Generado desde el diseño (`personal`, `deletion` y `retention`, DSL 2.28). No se edita: cambia el diseño y vuelve a generar.

| Entidad | Campo | Obligatorio | Borrado | Retención |
|---|---|---|---|---|
| Order | customerRef | sí | físico | sin retención declarada |
| Order | customerEmail | sí | físico | sin retención declarada |

## Lo que garantiza el servidor

- Ningún log ni traza que escribe el servidor lleva el valor de un campo de esta tabla, tampoco el mensaje del motor al violar una unicidad. El gate `check-logging` impide que lo haga el código escrito a mano.
- Al anonimizar, un campo opcional queda vacío y uno obligatorio recibe `anon-<id>`, que no deriva del valor y conserva la unicidad.
- Un borrado lógico oculta la fila a toda consulta, total de página y lectura por id o por clave natural; la siguen viendo las referencias desde otro agregado.
