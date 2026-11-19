package com.ecomerce.payment.domain.aggregate;

import com.ecomerce.payment.domain.entity.PaymentMethod;
import com.ecomerce.payment.domain.errors.InvariantViolatedException;
import com.ecomerce.payment.domain.errors.PaymentMethodLimitReachedError;
import com.ecomerce.payment.domain.errors.PaymentMethodNotFoundError;
import com.ecomerce.payment.domain.identity.Uuids;
import com.ecomerce.payment.domain.valueobject.CardDetails;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Los medios de pago guardados de un titular. Nace con el primer medio que se le guarda.
 */
public class Wallet {

    private UUID id = Uuids.v7();

    private String customerRef;

    private List<PaymentMethod> paymentMethods = new ArrayList<>();

    // Versión de concurrencia optimista del agregado; la gestiona el @Version
    // de la XxxJpa. Null hasta la primera persistencia.
    private Long lockVersion;

    /** Tope de medios active de un Wallet (invariante del diseño). */
    public static final int MAX_ACTIVE_METHODS = 10;

    /** Un Wallet nace con el primer medio que se le guarda (lo añade addPaymentMethod). */
    public static Wallet create(String customerRef) {
        if (customerRef == null || customerRef.isBlank()) {
            throw new InvariantViolatedException("Wallet: customerRef es obligatorio.");
        }
        return new Wallet(Uuids.v7(), customerRef, new ArrayList<>(), null);
    }

    /** Invariante: como mucho 10 medios active. Se comprueba antes de llamar a la pasarela. */
    public void requireCapacity() {
        if (activeCount() >= MAX_ACTIVE_METHODS) {
            throw new PaymentMethodLimitReachedError("El titular ya tiene " + MAX_ACTIVE_METHODS + " medios active");
        }
    }

    /**
     * Añade un medio. Queda por defecto si makeDefault es true o si el titular no tenía ningún medio active; el
     * anterior por defecto deja de serlo (a lo sumo uno active con isDefault).
     */
    public PaymentMethod addPaymentMethod(UUID methodId, String gatewayMethodRef, CardDetails card, boolean makeDefault) {
        requireCapacity();
        boolean asDefault = makeDefault || activeCount() == 0;
        if (asDefault) {
            paymentMethods.forEach(PaymentMethod::unmarkDefault);
        }
        PaymentMethod method = PaymentMethod.create(methodId, gatewayMethodRef, card, asDefault);
        paymentMethods.add(method);
        return method;
    }

    /** El medio active con ese id, si existe en este Wallet. */
    public Optional<PaymentMethod> findActive(UUID methodId) {
        return paymentMethods.stream().filter(method -> method.getId().equals(methodId) && method.isActive()).findFirst();
    }

    /** El medio con ese id, active o removed. */
    public Optional<PaymentMethod> findMethod(UUID methodId) {
        return paymentMethods.stream().filter(method -> method.getId().equals(methodId)).findFirst();
    }

    /** Marca un medio active como el preferente; si ya lo era no cambia nada. */
    public PaymentMethod setDefault(UUID methodId) {
        PaymentMethod target = findActive(methodId)
                .orElseThrow(() -> new PaymentMethodNotFoundError("El medio no existe, está retirado o no es del titular"));
        if (!Boolean.TRUE.equals(target.getIsDefault())) {
            paymentMethods.forEach(PaymentMethod::unmarkDefault);
            target.markDefault();
        }
        return target;
    }

    /**
     * active → removed. Si era el preferente, el titular se queda sin medio por defecto.
     *
     * @return false si ya estaba retirado (no hace nada)
     */
    public boolean removePaymentMethod(UUID methodId) {
        PaymentMethod target = findMethod(methodId)
                .orElseThrow(() -> new PaymentMethodNotFoundError("El medio no existe o no es del titular"));
        if (!target.isActive()) {
            return false;
        }
        target.remove();
        return true;
    }

    private long activeCount() {
        return paymentMethods.stream().filter(PaymentMethod::isActive).count();
    }

    // Rehidratación desde persistencia (lo usa el toDomain del adaptador de repositorio):
    // el estado ya es válido y no se revalida. La creación de negocio va por el factory.
    public Wallet(UUID id, String customerRef, List<PaymentMethod> paymentMethods, Long lockVersion) {
        this.id = id;
        this.customerRef = customerRef;
        this.paymentMethods = new ArrayList<>(paymentMethods);
        this.lockVersion = lockVersion;
    }

    // ─── La guarda del agregado (DSL 2.31) ───────────────────────────────────
    // La emite build y la llama el save del adaptador antes de escribir nada: recalcula los derivados y comprueba las
    // cotas de los campos y los checks del diseño, las hijas primero (el orden decide el code cuando se rompen dos).
    // No la llames desde el handler ni repitas sus comprobaciones en los métodos de negocio.
    public void ensureInvariants() {
        for (PaymentMethod child : paymentMethods) {
            child.ensureInvariants();
        }
        // Las cotas de sus campos (U1, U6: una cota de la entidad es siempre verdad).
        if (customerRef != null && (customerRef.codePointCount(0, customerRef.length()) < 1 || customerRef.codePointCount(0, customerRef.length()) > 200)) {
            throw new InvariantViolatedException("Wallet: customerRef queda fuera de sus cotas.");
        }
    }

    public UUID getId() {
        return id;
    }

    public String getCustomerRef() {
        return customerRef;
    }

    public List<PaymentMethod> getPaymentMethods() {
        return List.copyOf(paymentMethods);
    }

    public Long getLockVersion() {
        return lockVersion;
    }
}
