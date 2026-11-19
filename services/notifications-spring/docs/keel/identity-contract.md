# El contrato con el proveedor de identidad

> Generado por `build` desde el descriptor del proveedor (keel-core). **No se edita a mano**: se regenera en cada build.

El servicio es un resource server: no crea usuarios, grupos ni clientes, solo valida el access token y lee sus claims.
Funciona con Keycloak si el token que emite tiene la forma de este documento. Ninguna prueba del proyecto comprueba que un
despliegue concreto esté bien configurado —las pruebas firman sus propios tokens, y el proveedor de prueba los emite con
la forma esperada—: lo comprueba esta lista.

- **Proveedor elegido:** Keycloak.
- **Nivel de verificación:** medido contra un Keycloak real (la red security-check de los generadores).

## El token que el servidor acepta

- **Emisor y firma**: los de `OAUTH2_ISSUER_URI` (`https://<host>/realms/<realm>`); las claves, de su JWKS.
- **Roles**: lista de textos en `realm_access.roles`: `notifications-admin`, `notifications-auditor`, `notifications-operator`, `template-editor`.
- **Principal**: `preferred_username`.
- **Scopes**: `scope`, separados por espacios y sin prefijo.
- **Audiencia**: `aud` incluye `notifications` (`SECURITY_AUDIENCE`) en los tokens de los clientes máquina.
- **Cliente máquina**: `azp` o `client_id`, con el nombre que declara el diseño.

## Lo que hay que crear en Keycloak

- **Roles del realm**: `notifications-admin`, `notifications-auditor`, `notifications-operator`, `template-editor`.
- **Client scopes del realm, asignados como opcionales a cada cliente máquina**: `bounce:report`, `notification:read`, `notification:send`.
- **Clientes confidenciales con service account**, uno por cliente del diseño:
  - `billing`: scopes `notification:send`, `notification:read`.
  - `shipping`: scopes `notification:send`, `notification:read`.
  - `dispatch-only`: scopes `notification:send`.
  - `mail-provider`: scopes `bounce:report`.

## Claims que el diseño lee además de los anteriores

Ninguno: el servidor no lee del token nada más que lo de arriba.

## Variables

| Variable | Qué es |
|---|---|
| `OAUTH2_ISSUER_URI` | El emisor de los tokens: `https://<host>/realms/<realm>`. |
| `SECURITY_AUDIENCE` | La audiencia que tienen que traer los tokens de los clientes máquina. |
