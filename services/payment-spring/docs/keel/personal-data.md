# Datos personales de payment

Generado desde el diseño (`personal`, `deletion` y `retention`, DSL 2.28). No se edita: cambia el diseño y vuelve a generar.

| Entidad | Campo | Obligatorio | Borrado | Retención |
|---|---|---|---|---|
| Payment | customerRef | sí | físico | sin retención declarada |
| Refund | reason | no | físico | sin retención declarada |
| Wallet | customerRef | sí | físico | sin retención declarada |
| PaymentMethod | gatewayMethodRef | sí | físico | sin retención declarada |
| PaymentMethod | card | sí | físico | sin retención declarada |

## Lo que garantiza el servidor

- Ningún log ni traza que escribe el servidor lleva el valor de un campo de esta tabla, tampoco el mensaje del motor al violar una unicidad. El gate `check-logging` impide que lo haga el código escrito a mano.
- Al anonimizar, un campo opcional queda vacío y uno obligatorio recibe `anon-<id>`, que no deriva del valor y conserva la unicidad.
- Un borrado lógico oculta la fila a toda consulta, total de página y lectura por id o por clave natural; la siguen viendo las referencias desde otro agregado.

## En el historial de cambios

El historial (`persistence.audit.history`, DSL 2.29) guarda el valor anterior y el nuevo de cada campo que cambia, también de estos:

| Raíz | Rutas personales | Régimen |
|---|---|---|
| Payment | `customerRef`, `refunds[<id>].reason` | sin retención declarada: las entradas no caducan |
| Wallet | `customerRef`, `paymentMethods[<id>].card.brand`, `paymentMethods[<id>].card.last4`, `paymentMethods[<id>].card.expMonth`, `paymentMethods[<id>].card.expYear` | sin retención declarada: las entradas no caducan |

- Dar de baja la raíz deja una entrada `deleted` y conserva las anteriores: solo la retención se las lleva o las reescribe, en la misma transacción que la raíz.
- Ningún log ni traza lleva el `before` ni el `after` de una de estas rutas.
