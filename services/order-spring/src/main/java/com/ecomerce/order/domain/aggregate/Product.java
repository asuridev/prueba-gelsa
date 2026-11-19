package com.ecomerce.order.domain.aggregate;

import com.ecomerce.order.domain.errors.InvariantViolatedException;
import com.ecomerce.order.domain.valueobject.CurrencyCodeFormat;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Copia local (réplica) de los productos vendibles de catalog: solo los campos que order lee para calcular el importe. La mantienen los eventos de catalog; no es fuente de verdad. Mientras catalog no exista, la alimenta un emisor que emula sus eventos.
 */
public class Product {

    // Id del producto en catalog; lo asigna catalog, no este servicio.
    private UUID id;

    private String sku;

    private String name;

    private BigDecimal price;

    private String currency;

    // Lápida: catalog retiró el producto. Se conserva la fila con su versión para que un alta antigua que llegue tarde no lo resucite; no se puede pedir.
    private Boolean retired = false;

    // Versión del producto en catalog; un evento con versión menor o igual a la guardada se descarta.
    private Long sourceVersion;

    // Versión de concurrencia optimista del agregado; la gestiona el @Version
    // de la XxxJpa. Null hasta la primera persistencia.
    private Long lockVersion;

    /**
     * Factory de la copia local: la crea el projector con lo que informa catalog. El id lo asigna
     * catalog, no este servicio. Una lápida (retired) llega sin datos de producto.
     */
    public static Product projectionOf(UUID id, String sku, String name, BigDecimal price, String currency, Boolean retired, Long sourceVersion) {
        Objects.requireNonNull(id, "id");
        CurrencyCodeFormat.validate(currency);
        return new Product(id, sku, name, price, currency, retired != null ? retired : Boolean.FALSE, sourceVersion, null);
    }

    /**
     * Actualiza la copia con el estado que informa catalog: solo se asignan los campos informados
     * (una lápida conserva los datos que ya tenía); retired y sourceVersion siempre.
     */
    public void applySnapshot(String sku, String name, BigDecimal price, String currency, Boolean retired, Long sourceVersion) {
        CurrencyCodeFormat.validate(currency);
        if (sku != null) {
            this.sku = sku;
        }
        if (name != null) {
            this.name = name;
        }
        if (price != null) {
            this.price = price;
        }
        if (currency != null) {
            this.currency = currency;
        }
        if (retired != null) {
            this.retired = retired;
        }
        if (sourceVersion != null) {
            this.sourceVersion = sourceVersion;
        }
    }

    /** ¿Se puede pedir? Una lápida no. */
    public boolean isAvailable() {
        return !Boolean.TRUE.equals(retired);
    }

    // Rehidratación desde persistencia (lo usa el toDomain del adaptador de repositorio):
    // el estado ya es válido y no se revalida. La creación de negocio va por el factory.
    public Product(UUID id, String sku, String name, BigDecimal price, String currency, Boolean retired, Long sourceVersion, Long lockVersion) {
        this.id = id;
        this.sku = sku;
        this.name = name;
        this.price = price;
        this.currency = currency;
        this.retired = retired;
        this.sourceVersion = sourceVersion;
        this.lockVersion = lockVersion;
    }

    private static final Pattern CURRENCY_PATTERN = Pattern.compile("^[A-Z]{3}$");

    // ─── La guarda del agregado (DSL 2.31) ───────────────────────────────────
    // La emite build y la llama el save del adaptador antes de escribir nada: recalcula los derivados y comprueba las
    // cotas de los campos y los checks del diseño, las hijas primero (el orden decide el code cuando se rompen dos).
    // No la llames desde el handler ni repitas sus comprobaciones en los métodos de negocio.
    public void ensureInvariants() {
        // Las cotas de sus campos (U1, U6: una cota de la entidad es siempre verdad).
        if (sku != null && (sku.codePointCount(0, sku.length()) > 64)) {
            throw new InvariantViolatedException("Product: sku queda fuera de sus cotas.");
        }
        if (name != null && (name.codePointCount(0, name.length()) > 200)) {
            throw new InvariantViolatedException("Product: name queda fuera de sus cotas.");
        }
        if (price != null && (price.compareTo(new BigDecimal("0")) < 0)) {
            throw new InvariantViolatedException("Product: price queda fuera de sus cotas.");
        }
        if (currency != null && (!CURRENCY_PATTERN.matcher(currency).matches())) {
            throw new InvariantViolatedException("Product: currency queda fuera de sus cotas.");
        }
        // Sus checks (domain: entities.Product.checks).
        // check 0: sku es obligatorio cuando retired es false.
        if (Objects.equals(retired, false) && isAbsent(sku)) {
            throw new InvariantViolatedException("Product: sku es obligatorio cuando retired es false.");
        }
        // check 1: name es obligatorio cuando retired es false.
        if (Objects.equals(retired, false) && isAbsent(name)) {
            throw new InvariantViolatedException("Product: name es obligatorio cuando retired es false.");
        }
        // check 2: price es obligatorio cuando retired es false.
        if (Objects.equals(retired, false) && (price == null)) {
            throw new InvariantViolatedException("Product: price es obligatorio cuando retired es false.");
        }
        // check 3: currency es obligatorio cuando retired es false.
        if (Objects.equals(retired, false) && isAbsent(currency)) {
            throw new InvariantViolatedException("Product: currency es obligatorio cuando retired es false.");
        }
        // check 4: price > 0.
        if (!(Objects.isNull(price) || decimalOf(price).compareTo(new BigDecimal("0")) > 0)) {
            throw new InvariantViolatedException("Product: price > 0.");
        }
    }

    private static boolean isAbsent(String value) {
        return value == null || value.isEmpty();
    }

    private static BigDecimal decimalOf(Object value) {
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }

    public UUID getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getCurrency() {
        return currency;
    }

    public Boolean getRetired() {
        return retired;
    }

    public Long getSourceVersion() {
        return sourceVersion;
    }

    public Long getLockVersion() {
        return lockVersion;
    }
}
