# Stubs del proveedor de prueba (WireMock)

Los servicios de los que depende `order` por HTTP no están en `infra/`: en su
lugar hay un WireMock en `http://localhost:8090` (`http://wiremock:8080` desde otro
contenedor), y las `base-url` de los clientes de `http-clients` apuntan ahí en local.

**Lo normal es no tocar este directorio.** Cada prueba de integración programa lo que
necesita desde el arnés (`stubFor(...)` en `AbstractFlowIT`) y lo verifica ahí mismo:
así el escenario se lee entero en un sitio, y `infra/reset-db.sh` lo deja limpio entre
flujos. Un mapping en un archivo es estado global compartido por toda la suite.

Un `mappings/*.json` solo se justifica para lo que no pertenece a ningún flujo (por
ejemplo, un endpoint que el proveedor expone siempre igual y que se consulta al
arrancar). Formato y opciones: <https://wiremock.org/docs/stubbing/>.
