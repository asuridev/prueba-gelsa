# Lo que postgresql no sostiene de este diseño

> Generado por `keel-spring build` desde la matriz de paridad del generador. **No se edita a
> mano**: se regenera en cada build.

El diseño declara garantías de forma agnóstica; el motor las sostiene o no. Este documento dice
cuáles de las que **este diseño pidió** no las sostiene **este motor**, qué pasa en su lugar y
cuáles son las salidas. Existe porque el modo de fallo de una garantía ausente es silencioso: nada
lanza, nada se registra, y el hueco aparece el día que dos peticiones simultáneas hacen lo que el
diseño decía que no podía pasar.

## Ninguna

Sobre `postgresql`, todas las garantías que este diseño declara tienen con qué sostenerse. Esto no
es una ausencia de comprobación: es el resultado de contrastar el diseño contra la matriz del
generador, y se vuelve a comprobar en cada `build`.
