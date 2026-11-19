#!/bin/sh
# Deja el sistema listo para validar el flujo haciendo peticiones solo a order.
# Lo ejecuta el servicio `seed` del docker-compose.yaml de la raíz cuando los tres
# servicios están sanos. Es idempotente: se puede relanzar con `compose run --rm seed`.
set -eu

KC=http://keycloak:8080
RABBIT=http://guest:guest@rabbitmq:15672/api
NOTIFICATIONS=http://notifications:8080/api/v1

# Petición HTTP que devuelve el código de estado y deja el cuerpo en /tmp/body.
http() {
  curl -s -o /tmp/body -w '%{http_code}' "$@"
}

fail() {
  echo "ERROR: $1" >&2
  cat /tmp/body >&2 2>/dev/null || true
  echo >&2
  exit 1
}

# ── 1. RabbitMQ: que NotificationRequested de order llegue a notifications ─────────
# order publica en el exchange order.events; notifications escucha en
# any-registered-system.events (nombre fijo en su código). Un binding exchange→exchange
# los une sin tocar ninguno de los dos servicios.
echo "== RabbitMQ: order.events -> any-registered-system.events"
for ex in order.events any-registered-system.events; do
  code=$(http -X PUT -H 'content-type: application/json' \
    -d '{"type":"topic","durable":true,"auto_delete":false,"internal":false,"arguments":{}}' \
    "$RABBIT/exchanges/%2F/$ex")
  case "$code" in 201|204) ;; *) fail "no se pudo declarar el exchange $ex ($code)";; esac
done
code=$(http -X POST -H 'content-type: application/json' -d '{"routing_key":"#","arguments":{}}' \
  "$RABBIT/bindings/%2F/e/order.events/e/any-registered-system.events")
case "$code" in 201|204) echo "   ok";; *) fail "no se pudo crear el binding ($code)";; esac

# ── 2. Keycloak ─────────────────────────────────────────────────────────────────────
echo "== Keycloak: token de administración"
ADMIN_TOKEN=$(curl -sf -d grant_type=password -d client_id=admin-cli -d username=admin -d password=admin \
  "$KC/realms/master/protocol/openid-connect/token" | jq -r .access_token)
[ -n "$ADMIN_TOKEN" ] && [ "$ADMIN_TOKEN" != null ] || fail "no se obtuvo el token de admin de Keycloak"
AUTH="Authorization: Bearer $ADMIN_TOKEN"

# Cliente M2M con el que order llama a payment (scopes payment:charge y payment:read).
echo "== Keycloak: cliente M2M 'order' en el realm payment"
code=$(http -X POST -H "$AUTH" -H 'content-type: application/json' "$KC/admin/realms/payment/clients" \
  -d "$(jq -n --arg s "$ORDER_PAYMENT_CLIENT_SECRET" '{clientId:"order", enabled:true, publicClient:false,
        secret:$s, serviceAccountsEnabled:true, standardFlowEnabled:false, directAccessGrantsEnabled:false}')")
case "$code" in 201) echo "   creado";; 409) echo "   ya existía";; *) fail "no se pudo crear el cliente order ($code)";; esac
CLIENT_ID=$(curl -sf -H "$AUTH" "$KC/admin/realms/payment/clients?clientId=order" | jq -r '.[0].id')
for scope in aud-payment payment:charge payment:read; do
  SCOPE_ID=$(curl -sf -H "$AUTH" "$KC/admin/realms/payment/client-scopes" | jq -r --arg n "$scope" '.[] | select(.name==$n) | .id')
  [ -n "$SCOPE_ID" ] || fail "el realm payment no tiene el client scope $scope"
  code=$(http -X PUT -H "$AUTH" "$KC/admin/realms/payment/clients/$CLIENT_ID/default-client-scopes/$SCOPE_ID")
  case "$code" in 204) ;; *) fail "no se pudo asignar el scope $scope al cliente order ($code)";; esac
done
echo "   scopes: aud-payment, payment:charge, payment:read"

# Comprador del realm order: su email sale del token, nunca del cuerpo del pedido.
echo "== Keycloak: comprador '$BUYER_USERNAME' en el realm order"
code=$(http -X POST -H "$AUTH" -H 'content-type: application/json' "$KC/admin/realms/order/users" \
  -d "$(jq -n --arg u "$BUYER_USERNAME" --arg p "$BUYER_PASSWORD" --arg e "$BUYER_EMAIL" '{username:$u, email:$e,
        emailVerified:true, firstName:"Ana", lastName:"Compradora", enabled:true, requiredActions:[],
        credentials:[{type:"password", value:$p, temporary:false}]}')")
case "$code" in 201) echo "   creado";; 409) echo "   ya existía";; *) fail "no se pudo crear el comprador ($code)";; esac

# ── 3. notifications: remitente y plantillas de los tres correos de order ───────────
echo "== notifications: token de back-office (notifications-admin)"
ADMIN_PASSWORD=$(jq -r '.users[] | select(.username=="notifications-admin") | .credentials[0].value' /realms/notifications.json)
NOTIF_TOKEN=$(curl -sf -d grant_type=password -d client_id=notifications-spring-test \
  -d username=notifications-admin --data-urlencode "password=$ADMIN_PASSWORD" \
  "$KC/realms/notifications/protocol/openid-connect/token" | jq -r .access_token)
[ -n "$NOTIF_TOKEN" ] && [ "$NOTIF_TOKEN" != null ] || fail "no se obtuvo el token de notifications-admin"
NAUTH="Authorization: Bearer $NOTIF_TOKEN"

echo "== notifications: remitente"
code=$(http -X PUT -H "$NAUTH" -H 'content-type: application/json' "$NOTIFICATIONS/sender-settings" \
  -d '{"senderAddress":"tienda@example.com","senderName":"Tienda de prueba","replyToAddress":"soporte@example.com"}')
case "$code" in 200|201|204) echo "   ok";; *) fail "no se pudo configurar el remitente ($code)";; esac

# publish_template <code> <nombre> <asunto> <texto> <¿pide failureReason? true|false>
publish_template() {
  tcode=$1 tname=$2 subject=$3 text=$4 with_reason=$5
  code=$(http -X POST -H "$NAUTH" -H 'content-type: application/json' "$NOTIFICATIONS/templates" \
    -d "$(jq -n --arg c "$tcode" --arg n "$tname" '{code:$c, name:$n, description:"Correo al comprador que envía order"}')")
  case "$code" in 200|201|409) ;; *) fail "no se pudo crear la plantilla $tcode ($code)";; esac

  active=$(curl -sf -H "$NAUTH" "$NOTIFICATIONS/templates/$tcode" | jq -r '.hasActiveVersion // false')
  if [ "$active" = true ]; then
    echo "   $tcode: ya tenía una versión activa"
    return
  fi
  code=$(http -X POST -H "$NAUTH" -H 'content-type: application/json' "$NOTIFICATIONS/templates/$tcode/versions" \
    -d "$(jq -n --arg s "$subject" --arg t "$text" --argjson r "$with_reason" '{
          subject: $s,
          textBody: $t,
          htmlBody: ("<p>" + ($t | gsub("\n"; "<br>")) + "</p>"),
          variables: ([
            {name:"orderId", description:"Id del pedido", required:true},
            {name:"totalAmount", description:"Importe total", required:true},
            {name:"currency", description:"Moneda", required:true}
          ] + (if $r then [{name:"failureReason", description:"Motivo del fallo del cobro", required:true}] else [] end))
        }')")
  case "$code" in 200|201) echo "   $tcode: versión publicada";; *) fail "no se pudo publicar la versión de $tcode ($code)";; esac
}

echo "== notifications: plantillas"
publish_template order-paid "Pedido pagado" "Tu pedido {{orderId}} está pagado" \
  "Hemos cobrado {{totalAmount}} {{currency}} por tu pedido {{orderId}}. ¡Gracias por tu compra!" false
publish_template order-payment-failed "Pago fallido" "No pudimos cobrar tu pedido {{orderId}}" \
  "El cobro de {{totalAmount}} {{currency}} de tu pedido {{orderId}} no se completó (motivo: {{failureReason}})." true
publish_template order-canceled "Pedido cancelado" "Tu pedido {{orderId}} se ha cancelado" \
  "Tu pedido {{orderId}} por {{totalAmount}} {{currency}} se ha cancelado y no se te ha cobrado nada." false

# ── 4. Catálogo: catalog no existe, así que se emulan sus eventos ProductUpserted ───
echo "== Catálogo: productos en la réplica de order"
publish_product() {
  envelope=$(jq -n --arg eid "$(cat /proc/sys/kernel/random/uuid)" --arg at "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    --arg id "$1" --arg sku "$2" --arg name "$3" --argjson price "$4" '{
      metadata: {eventId:$eid, eventType:"ProductUpserted", eventVersion:1, occurredAt:$at, source:"catalog"},
      data: {productId:$id, sku:$sku, name:$name, price:$price, currency:"EUR", version:1}
    }')
  routed=$(curl -sf -H 'content-type: application/json' "$RABBIT/exchanges/%2F/catalog.events/publish" \
    -d "$(jq -n --arg p "$envelope" '{properties:{content_type:"application/json"},
          routing_key:"catalog.product-upserted", payload:$p, payload_encoding:"string"}')" | jq -r .routed)
  [ "$routed" = true ] || fail "el evento de $2 no llegó a ninguna cola (¿arrancó order?)"
  echo "   $1  $2  $3  $4 EUR"
}
publish_product 11111111-1111-4111-8111-111111111111 TSHIRT-001 "Camiseta" 19.90
publish_product 22222222-2222-4222-8222-222222222222 MUG-001 "Taza" 9.50
publish_product 33333333-3333-4333-8333-333333333333 BOOK-001 "Libro" 25.00

echo ""
echo "Siembra completa. Ya se pueden hacer pedidos a order: ver compose/requests.http"
