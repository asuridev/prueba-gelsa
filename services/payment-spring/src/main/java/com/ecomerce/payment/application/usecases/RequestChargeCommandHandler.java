package com.ecomerce.payment.application.usecases;

import com.ecomerce.payment.application.annotations.ApplicationComponent;
import com.ecomerce.payment.application.annotations.LogExceptions;
import com.ecomerce.payment.application.commands.RequestChargeCommand;
import com.ecomerce.payment.application.dtos.RequestChargeResponseDto;
import com.ecomerce.payment.application.interfaces.ReturningCommandHandler;
import com.ecomerce.payment.application.mappers.PaymentApplicationMapper;
import com.ecomerce.payment.application.payment.PaymentOutcomeApplier;
import com.ecomerce.payment.application.port.out.PaymentGateway;
import com.ecomerce.payment.domain.aggregate.Payment;
import com.ecomerce.payment.domain.entity.PaymentMethod;
import com.ecomerce.payment.domain.errors.ChargeRequestConflictError;
import com.ecomerce.payment.domain.errors.PaymentMethodUnavailableError;
import com.ecomerce.payment.domain.errors.PaymentNotFoundError;
import com.ecomerce.payment.domain.errors.PaymentSourceInvalidError;
import com.ecomerce.payment.domain.payment.ChargeRequest;
import com.ecomerce.payment.domain.payment.GatewayOutcome;
import com.ecomerce.payment.domain.payment.GatewayStatus;
import com.ecomerce.payment.domain.payment.PaymentGatewayUnavailableException;
import com.ecomerce.payment.domain.payment.PaymentSource;
import com.ecomerce.payment.domain.repository.PaymentRepository;
import com.ecomerce.payment.domain.repository.WalletRepository;
import java.util.Objects;

/**
 * Pide un cobro (autorización) con un token de un solo uso o con un medio guardado del titular. Registra el cobro en pending antes de llamar a la pasarela y responde con el cobro en el estado que haya alcanzado: authorized, actionRequired, failed, o pending si la pasarela no contestó.
 *
 * <p>Se despacha SIN transacción abarcadora: el cobro en pending se confirma en su propia transacción ANTES de llamar a
 * la pasarela, y el desenlace se aplica en otra. La guarda del doble cobro es la clave natural (chargeRequestId), no un
 * almacén de idempotencia.
 */
@ApplicationComponent
public class RequestChargeCommandHandler implements ReturningCommandHandler<RequestChargeCommand, RequestChargeResponseDto> {

    private final PaymentRepository paymentRepository;
    private final PaymentApplicationMapper paymentApplicationMapper;
    private final WalletRepository walletRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentOutcomeApplier outcomeApplier;

    public RequestChargeCommandHandler(PaymentRepository paymentRepository, PaymentApplicationMapper paymentApplicationMapper,
            WalletRepository walletRepository, PaymentGateway paymentGateway, PaymentOutcomeApplier outcomeApplier) {
        this.paymentRepository = paymentRepository;
        this.paymentApplicationMapper = paymentApplicationMapper;
        this.walletRepository = walletRepository;
        this.paymentGateway = paymentGateway;
        this.outcomeApplier = outcomeApplier;
    }

    @Override
    @LogExceptions
    public RequestChargeResponseDto handle(RequestChargeCommand command) {
        // 1. La clave se comprueba antes que cualquier otra guarda: el mismo cobro se devuelve tal como esté.
        Payment existing = paymentRepository.findByChargeRequestId(command.chargeRequestId()).orElse(null);
        if (existing != null) {
            return paymentApplicationMapper.toRequestChargeResponseDto(replayOrConflict(existing, command));
        }

        // 2. Exactamente una fuente de pago.
        boolean hasToken = command.paymentToken() != null && !command.paymentToken().isBlank();
        boolean hasSaved = command.paymentMethodId() != null;
        if (hasToken == hasSaved) {
            throw new PaymentSourceInvalidError("Se indica exactamente una fuente de pago: paymentToken o paymentMethodId");
        }

        // 3 y 4. Moneda vigente de ISO 4217 y decimales según la moneda (en ese orden).
        Payment.requireKnownCurrency(command.currency());
        Payment.requireAmountScale(command.amount(), command.currency());

        // 5. El medio existe, está active y es del titular: no se distingue cuál de las tres falla.
        PaymentSource source;
        if (hasSaved) {
            PaymentMethod method = walletRepository.findByCustomerRef(command.customerRef())
                    .flatMap(wallet -> wallet.findActive(command.paymentMethodId()))
                    .orElseThrow(() -> new PaymentMethodUnavailableError("El medio no existe, está retirado o no es del titular"));
            source = PaymentSource.saved(method.getGatewayMethodRef());
        } else {
            source = PaymentSource.token(command.paymentToken());
        }

        // 6. El cobro se registra en pending (con awaitingSince) y CONFIRMA antes de llamar a la pasarela. Si choca con
        // la clave natural otra petición idéntica a la vez, se relee al ganador y se aplica la regla de la repetición.
        Payment registered = paymentRepository.register(Payment.create(
                command.chargeRequestId(), command.customerRef(), command.amount(), command.currency(), command.paymentMethodId())).orElse(null);
        if (registered == null) {
            Payment winner = paymentRepository.findByChargeRequestId(command.chargeRequestId())
                    .orElseThrow(() -> new PaymentNotFoundError("No existe un cobro con ese chargeRequestId"));
            return paymentApplicationMapper.toRequestChargeResponseDto(replayOrConflict(winner, command));
        }

        // 7. La pasarela. Un rechazo no es un error de la petición: el cobro queda failed. Sin respuesta, no se
        // reintenta: el cobro se queda en pending y lo resuelve el barrido.
        GatewayOutcome outcome;
        try {
            outcome = paymentGateway.authorize(new ChargeRequest(registered.getChargeRequestId(), registered.getAmount(), registered.getCurrency(), source));
        } catch (PaymentGatewayUnavailableException noAnswer) {
            return paymentApplicationMapper.toRequestChargeResponseDto(registered);
        }
        if (outcome.status() != GatewayStatus.PENDING && outcome.status() != GatewayStatus.NOT_FOUND) {
            outcomeApplier.apply(new GatewayOutcome(outcome.status(), registered.getChargeRequestId(), outcome.gatewayPaymentId(),
                    outcome.failureReason(), outcome.customerAction(), outcome.refundedAmount()));
        }
        // Si al aplicar el desenlace el cobro ya salió de pending por otra vía (el aviso), se relee y se responde como esté.
        return paymentRepository.findByChargeRequestId(command.chargeRequestId())
                .map(paymentApplicationMapper::toRequestChargeResponseDto)
                .orElseThrow(() -> new PaymentNotFoundError("No existe un cobro con ese chargeRequestId"));
    }

    /**
     * Mismo customerRef, amount, currency y fuente de pago → el cobro existente. La fuente se compara por su clase
     * cuando es un token (no se guarda) y por paymentMethodId cuando es un medio guardado. Otro contenido →
     * CHARGE_REQUEST_CONFLICT.
     */
    private Payment replayOrConflict(Payment existing, RequestChargeCommand command) {
        boolean hasToken = command.paymentToken() != null && !command.paymentToken().isBlank();
        boolean hasSaved = command.paymentMethodId() != null;
        boolean sameSource = hasToken != hasSaved
                && (hasSaved ? Objects.equals(existing.getPaymentMethodId(), command.paymentMethodId()) : existing.getPaymentMethodId() == null);
        boolean same = sameSource
                && existing.getCustomerRef().equals(command.customerRef())
                && existing.getCurrency().equals(command.currency())
                && existing.getAmount().compareTo(command.amount()) == 0;
        if (!same) {
            throw new ChargeRequestConflictError("Ya existe un cobro con ese chargeRequestId y con otro contenido");
        }
        return existing;
    }
}
