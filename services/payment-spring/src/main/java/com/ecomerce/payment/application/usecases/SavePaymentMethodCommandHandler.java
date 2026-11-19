package com.ecomerce.payment.application.usecases;

import com.ecomerce.payment.application.annotations.ApplicationComponent;
import com.ecomerce.payment.application.annotations.LogExceptions;
import com.ecomerce.payment.application.commands.SavePaymentMethodCommand;
import com.ecomerce.payment.application.dtos.SavePaymentMethodResponseDto;
import com.ecomerce.payment.application.interfaces.ReturningCommandHandler;
import com.ecomerce.payment.application.mappers.PaymentMethodApplicationMapper;
import com.ecomerce.payment.application.port.out.PaymentGateway;
import com.ecomerce.payment.application.support.CommandSignature;
import com.ecomerce.payment.application.support.IdempotencyContext;
import com.ecomerce.payment.domain.aggregate.Wallet;
import com.ecomerce.payment.domain.entity.PaymentMethod;
import com.ecomerce.payment.domain.errors.GatewayUnavailableError;
import com.ecomerce.payment.domain.errors.IdempotencyKeyRequiredError;
import com.ecomerce.payment.domain.errors.PaymentMethodAuthenticationRequiredError;
import com.ecomerce.payment.domain.errors.PaymentMethodRejectedError;
import com.ecomerce.payment.domain.idempotency.IdempotencyConflictException;
import com.ecomerce.payment.domain.idempotency.IdempotencyReuseException;
import com.ecomerce.payment.domain.idempotency.IdempotencyStore;
import com.ecomerce.payment.domain.identity.Uuids;
import com.ecomerce.payment.domain.payment.PaymentGatewayUnavailableException;
import com.ecomerce.payment.domain.payment.PaymentMethodNotAcceptedException;
import com.ecomerce.payment.domain.payment.SavedPaymentMethod;
import com.ecomerce.payment.domain.repository.WalletRepository;
import com.ecomerce.payment.domain.valueobject.CardDetails;
import java.util.UUID;

/**
 * Guarda en el Wallet del titular el medio de pago que representa un token de la pasarela, para cobrarle después sin que esté presente.
 *
 * <p>Se despacha SIN transacción abarcadora (llama a la pasarela en medio), así que la clave de idempotencia se reclama
 * ANTES de llamarla —lo que arbitra la carrera— y se LIBERA si la operación termina en error: solo un guardado con
 * éxito queda registrado bajo la Idempotency-Key.
 */
@ApplicationComponent
public class SavePaymentMethodCommandHandler implements ReturningCommandHandler<SavePaymentMethodCommand, SavePaymentMethodResponseDto> {

    /** Ventana de deduplicación declarada por el diseño (idempotency.ttlSeconds). */
    private static final long IDEMPOTENCY_TTL_SECONDS = 86400;

    private final WalletRepository walletRepository;
    private final PaymentMethodApplicationMapper paymentMethodApplicationMapper;
    private final IdempotencyStore idempotencyStore;
    private final PaymentGateway paymentGateway;

    public SavePaymentMethodCommandHandler(WalletRepository walletRepository, PaymentMethodApplicationMapper paymentMethodApplicationMapper,
            IdempotencyStore idempotencyStore, PaymentGateway paymentGateway) {
        this.walletRepository = walletRepository;
        this.paymentMethodApplicationMapper = paymentMethodApplicationMapper;
        this.idempotencyStore = idempotencyStore;
        this.paymentGateway = paymentGateway;
    }

    @Override
    @LogExceptions
    public SavePaymentMethodResponseDto handle(SavePaymentMethodCommand command) {
        // 1. La cabecera Idempotency-Key es obligatoria en esta operación.
        String key = IdempotencyContext.get().orElseThrow(() -> new IdempotencyKeyRequiredError("La petición no trae la cabecera Idempotency-Key"));
        String scope = command.idempotencyScope();
        String signature = CommandSignature.of(command);

        // 2. Repetición de una clave ya vista: mismo contenido → la respuesta original; otro → IDEMPOTENCY_KEY_REUSED.
        IdempotencyStore.StoredRequest stored = idempotencyStore.find(scope, key).orElse(null);
        if (stored != null) {
            if (!stored.signature().equals(signature)) {
                throw new IdempotencyReuseException(scope, key);
            }
            return replay(command, UUID.fromString(stored.resourceId()), scope, key);
        }

        // 3. El tope de 10 medios active se comprueba ANTES de llamar a la pasarela (y antes de reclamar la clave).
        Wallet existingWallet = walletRepository.findByCustomerRef(command.customerRef()).orElse(null);
        if (existingWallet != null) {
            existingWallet.requireCapacity();
        }

        // 4. Se reclama la clave con el id del medio que se va a crear, ANTES del negocio: la carrera la arbitra la
        // clave primaria del registro y la perdedora recibe IDEMPOTENCY_KEY_IN_PROGRESS sin llegar a la pasarela.
        UUID methodId = Uuids.v7();
        idempotencyStore.save(scope, key, signature, methodId.toString(), IDEMPOTENCY_TTL_SECONDS);

        try {
            // 5. La pasarela: el medio se guarda solo si ella lo acepta. El token solo viaja a ella; no se guarda.
            SavedPaymentMethod saved = saveInGateway(command);

            // 6. El Wallet nace con el primer medio; el medio queda por defecto si makeDefault o si es el único active.
            Wallet wallet = walletRepository.findByCustomerRef(command.customerRef()).orElseGet(() -> Wallet.create(command.customerRef()));
            wallet.addPaymentMethod(methodId, saved.gatewayReference(),
                    new CardDetails(saved.brand(), saved.last4(), saved.expMonth(), saved.expYear()),
                    Boolean.TRUE.equals(command.makeDefault()));
            Wallet persisted = walletRepository.save(wallet);
            PaymentMethod method = persisted.findMethod(methodId).orElseThrow();
            return paymentMethodApplicationMapper.toSavePaymentMethodResponseDto(method, persisted.getId());
        } catch (RuntimeException failure) {
            // Solo un guardado con éxito queda registrado: con error, la clave queda libre.
            idempotencyStore.release(scope, key);
            throw failure;
        }
    }

    private SavedPaymentMethod saveInGateway(SavePaymentMethodCommand command) {
        try {
            return paymentGateway.savePaymentMethod(command.paymentToken(), command.customerRef());
        } catch (PaymentMethodNotAcceptedException notAccepted) {
            if (notAccepted.isAuthenticationRequired()) {
                throw new PaymentMethodAuthenticationRequiredError("La pasarela exige autenticar al cliente para guardar el medio");
            }
            throw new PaymentMethodRejectedError("La pasarela no acepta el token o rechaza el medio");
        } catch (PaymentGatewayUnavailableException noAnswer) {
            throw new GatewayUnavailableError("La pasarela no contestó; no se guardó nada y se puede reintentar con un token nuevo");
        }
    }

    /** Reconstruye la respuesta original desde el id del medio, sin re-ejecutar nada. Sin medio todavía: en curso. */
    private SavePaymentMethodResponseDto replay(SavePaymentMethodCommand command, UUID methodId, String scope, String key) {
        Wallet wallet = walletRepository.findByCustomerRef(command.customerRef()).orElse(null);
        PaymentMethod method = wallet == null ? null : wallet.findMethod(methodId).orElse(null);
        if (method == null) {
            // La ganadora reclamó la clave pero aún no confirmó el medio.
            throw new IdempotencyConflictException(scope, key, null);
        }
        return paymentMethodApplicationMapper.toSavePaymentMethodResponseDto(method, wallet.getId());
    }
}
