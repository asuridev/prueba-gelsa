# Sistema de pedidos: order · payment · notifications

Un comprador hace un pedido desde una SPA y la solución lo gestiona de principio a fin: **order** registra
el pedido y calcula el importe, **payment** lo cobra con tarjeta a través de Stripe y **notifications**
avisa al comprador por correo. Los servicios se comunican por HTTP y por eventos (RabbitMQ), se autentican
con Keycloak y cada uno guarda sus datos en su propia base PostgreSQL.

El repositorio contiene el **diseño** de cada servicio (artefactos declarativos agnósticos de tecnología, con
[Keel](docs/methodology.md)) y el **código** que se generó a partir de él con Spring Boot.

## Dónde está cada microservicio

| Microservicio | Qué hace | Diseño | Código |
|---|---|---|---|
| **order** | Recibe los pedidos, calcula el importe con su copia del catálogo y coordina el cobro y el aviso | [`specs/order`](specs/order/) · [DESIGN.md](docs/order/DESIGN.md) | [`services/order-spring`](services/order-spring/) |
| **payment** | Cobra con tarjeta: autoriza, captura, anula y devuelve, y guarda medios de pago | [`specs/payment`](specs/payment/) · [DESIGN.md](docs/payment/DESIGN.md) | [`services/payment-spring`](services/payment-spring/) |
| **notifications** | Envía correo transaccional desde plantillas, una sola vez por mensaje | [`specs/notifications`](specs/notifications/) · [DESIGN.md](docs/notifications/DESIGN.md) | [`services/notifications-spring`](services/notifications-spring/) |

Para entender cómo encajan, por qué están hechos así y qué pasa cuando algo falla, lee
[ARCHITECTURE.md](ARCHITECTURE.md). Los diagramas están en [`docs/diagrams`](docs/diagrams/).

> **Para levantar un servicio** hay que generar antes su configuración local con `keel-spring build`:
> `deploy/.env` e `infra/test-credentials.env` no se versionan porque llevan credenciales de prueba.

> Edita libremente esta introducción para describir tu organización, convenciones o cómo contribuir.
> La tabla de la sección **Servicios diseñados** la genera `keel index` (lo ejecutan `/keel-design` al
> cerrar un diseño, `/keel-handoff` y `/keel-docs`); **no edites a mano el contenido entre los
> marcadores**, se pisa en la siguiente ejecución. `keel index --check` comprueba, sin escribir, que el
> índice sigue al día.

## Servicios diseñados

Cada servicio enlaza, si existen: su **documento de diseño** (`DESIGN.md`: modelo de dominio, invariantes,
decisiones y cómo reutilizarlo), el **panel de revisión** (`overview.html`, de `/keel-docs`: el servicio
entero de un vistazo, con enlaces a los contratos y a las colecciones Postman), los visores de sus
**contratos formales** —**API** (`openapi.html`) y **eventos** (`asyncapi.html`)— y su **contrato
servidor-a-servidor** (`INTEGRATION.md`, de `/keel-integrate`).

> Los tres enlaces HTML solo se abren renderizados en GitHub si el workspace declara dónde está publicado.
> Crea un `publish.yaml` en esta raíz con `repo: <org>/<repo>` (y `branch:` si no es `main`) y vuelve a
> ejecutar `keel index`: sin él quedan como enlaces relativos, que GitHub muestra como código fuente.

<!-- keel:servicios:start -->
| Diseño | Dominio | Madurez | Resumen | Capas | Documentación |
|---|---|---|---|---|---|
| [`notifications`](specs/notifications/) | — | — | Envía correo transaccional a partir de plantillas que gestiona negocio, a petición de los sistemas de la organización por HTTP o por eventos, sin enviar nunca dos veces el mismo correo. | `domain`, `use-cases`, `api`, `security`, `messaging`, `persistence`, `mail` | [diseño](docs/notifications/DESIGN.md) · [panel](https://htmlpreview.github.io/?https://raw.githubusercontent.com/asuridev/prueba-gelsa/main/docs/notifications/overview.html) · [API](https://htmlpreview.github.io/?https://raw.githubusercontent.com/asuridev/prueba-gelsa/main/docs/notifications/openapi.html) · [eventos](https://htmlpreview.github.io/?https://raw.githubusercontent.com/asuridev/prueba-gelsa/main/docs/notifications/asyncapi.html) · [integración](docs/notifications/INTEGRATION.md) |
| [`order`](specs/order/) | commerce | — | Recibe los pedidos de los compradores, calcula su importe con la copia local del catálogo, pide el cobro a payment, sigue su desenlace y lo notifica al comprador a través de notifications. | `domain`, `use-cases`, `api`, `security`, `messaging`, `http-clients`, `dependencies`, `persistence` | [diseño](docs/order/DESIGN.md) |
| [`payment`](specs/payment/) | commerce | — | Cobra con tarjeta, a través de una pasarela intercambiable, los importes que le piden otros servidores — autorizar, capturar, anular y devolver —, y guarda medios de pago para cobrar sin el cliente presente. | `domain`, `use-cases`, `api`, `security`, `messaging`, `persistence`, `payments` | [diseño](docs/payment/DESIGN.md) · [panel](https://htmlpreview.github.io/?https://raw.githubusercontent.com/asuridev/prueba-gelsa/main/docs/payment/overview.html) · [API](https://htmlpreview.github.io/?https://raw.githubusercontent.com/asuridev/prueba-gelsa/main/docs/payment/openapi.html) · [eventos](https://htmlpreview.github.io/?https://raw.githubusercontent.com/asuridev/prueba-gelsa/main/docs/payment/asyncapi.html) · [integración](docs/payment/INTEGRATION.md) |
<!-- keel:servicios:end -->

composition.md`.
