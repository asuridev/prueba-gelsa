package com.ecomerce.order.domain.repository;

import com.ecomerce.order.domain.aggregate.Order;
import com.ecomerce.order.domain.enums.OrderStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Puerto de persistencia del agregado Order; el adaptador JPA vive en
 * infrastructure/persistence/repositories.
 */
public interface OrderRepository {

    Optional<Order> findById(UUID id);

    /**
     * Carga el agregado por su id si el alcance del llamante lo admite (security.ownership.Order.reads:
     * Order.customerRef = identidad del llamante). Vacío si no existe; si existe y es ajeno, devuelve vacío, como si no existiera. El scope lo estampa el borde.
     */
    Optional<Order> loadScoped(UUID id, ReadScope scope);

    /**
     * Los filtros de listMyOrders, como los declara el diseño (DSL 2.22). Un filtro a null no restringe.
     */
    record ListMyOrdersFilter(String customerRef, OrderStatus status) {
    }

    /** La búsqueda de listMyOrders con los filtros del diseño y solo esos, acotada al scope del llamante en las filas y en el total (DSL 2.27). */
    Page<Order> listMyOrders(ListMyOrdersFilter filter, Pageable pageable, ReadScope scope);

    /**
     * Reclama los Order que encargaron trabajo a payment.chargeOrder y
     * llevan demasiado tiempo sin desenlace.
     *
     * <p><b>Reclama, no lee.</b> Puede correr a la vez en varias réplicas: con
     * {@code @Scheduled}, en todas; con el cronómetro, cuando un tick se repite al vencer su arriendo. La lista
     * que devuelve son SOLO los candidatos que ESTA instancia se llevó: el reclamo es una
     * marca persistida en {@code reconciliation_claim} que se confirma antes de volver, así
     * que las demás réplicas dejan de verlos. Leerlos con un finder y actuar después haría
     * que las N llamaran al proveedor por el mismo encargo.
     *
     * <p>La marca CADUCA ({@code reconciliation.charge-order.claim-timeout-ms}): sobrevive
     * al commit, así que también sobreviviría a la réplica que muera con el candidato en
     * vuelo. Dimensiona el plazo por encima de lote × timeout de llamada.
     *
     * <p>No recibe el tamaño del lote: los tres números —umbral, caducidad y cota— salen de
     * {@code parameters/<perfil>/reconciliation.yaml}, y quien los lee es el adaptador.
     * Actúa sobre lo que devuelve FUERA de esta llamada.
     */
    List<Order> claimForReconcileStalledOrdersChargeOrder();

    /**
     * Reclama los Order que encargaron trabajo a payment.captureOrderPayment y
     * llevan demasiado tiempo sin desenlace.
     *
     * <p><b>Reclama, no lee.</b> Puede correr a la vez en varias réplicas: con
     * {@code @Scheduled}, en todas; con el cronómetro, cuando un tick se repite al vencer su arriendo. La lista
     * que devuelve son SOLO los candidatos que ESTA instancia se llevó: el reclamo es una
     * marca persistida en {@code reconciliation_claim} que se confirma antes de volver, así
     * que las demás réplicas dejan de verlos. Leerlos con un finder y actuar después haría
     * que las N llamaran al proveedor por el mismo encargo.
     *
     * <p>La marca CADUCA ({@code reconciliation.capture-order-payment.claim-timeout-ms}): sobrevive
     * al commit, así que también sobreviviría a la réplica que muera con el candidato en
     * vuelo. Dimensiona el plazo por encima de lote × timeout de llamada.
     *
     * <p>No recibe el tamaño del lote: los tres números —umbral, caducidad y cota— salen de
     * {@code parameters/<perfil>/reconciliation.yaml}, y quien los lee es el adaptador.
     * Actúa sobre lo que devuelve FUERA de esta llamada.
     */
    List<Order> claimForReconcileStalledOrdersCaptureOrderPayment();

    /**
     * Reclama los Order que encargaron trabajo a payment.cancelOrderPayment y
     * llevan demasiado tiempo sin desenlace.
     *
     * <p><b>Reclama, no lee.</b> Puede correr a la vez en varias réplicas: con
     * {@code @Scheduled}, en todas; con el cronómetro, cuando un tick se repite al vencer su arriendo. La lista
     * que devuelve son SOLO los candidatos que ESTA instancia se llevó: el reclamo es una
     * marca persistida en {@code reconciliation_claim} que se confirma antes de volver, así
     * que las demás réplicas dejan de verlos. Leerlos con un finder y actuar después haría
     * que las N llamaran al proveedor por el mismo encargo.
     *
     * <p>La marca CADUCA ({@code reconciliation.cancel-order-payment.claim-timeout-ms}): sobrevive
     * al commit, así que también sobreviviría a la réplica que muera con el candidato en
     * vuelo. Dimensiona el plazo por encima de lote × timeout de llamada.
     *
     * <p>No recibe el tamaño del lote: los tres números —umbral, caducidad y cota— salen de
     * {@code parameters/<perfil>/reconciliation.yaml}, y quien los lee es el adaptador.
     * Actúa sobre lo que devuelve FUERA de esta llamada.
     */
    List<Order> claimForReconcileStalledOrdersCancelOrderPayment();

    Order save(Order entity);

    void deleteById(UUID id);
}
