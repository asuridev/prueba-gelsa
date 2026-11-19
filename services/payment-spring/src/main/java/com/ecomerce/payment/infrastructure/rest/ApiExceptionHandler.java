package com.ecomerce.payment.infrastructure.rest;

import com.ecomerce.payment.domain.errors.BadRequestException;
import com.ecomerce.payment.domain.errors.BusinessException;
import com.ecomerce.payment.domain.errors.ChargeRequestConflictError;
import com.ecomerce.payment.domain.errors.ConcurrentModificationError;
import com.ecomerce.payment.domain.errors.ConflictException;
import com.ecomerce.payment.domain.errors.DomainException;
import com.ecomerce.payment.domain.errors.ForbiddenException;
import com.ecomerce.payment.domain.errors.NotFoundException;
import com.ecomerce.payment.domain.errors.PayloadTooLargeException;
import com.ecomerce.payment.domain.errors.RefundRequestConflictError;
import com.ecomerce.payment.domain.errors.UnauthorizedException;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /**
     * Nombre de constraint única → error de negocio que representa violarla.
     * La clave está en minúsculas: el nombre llega con la caja que le dé el
     * dialecto y se compara normalizado.
     */
    private static final Map<String, Supplier<DomainException>> CONSTRAINT_TO_ERROR = Map.ofEntries(
            // Unicidad de Payment.chargeRequestId → el error que el diseño NOMBRA para ella
            // (naturalKeyError / indexes[].error). No se deduce: lo dijo el diseño.
            Map.entry("uk_payments_natural", () -> new ChargeRequestConflictError(
                    "Ya existe un cobro con ese chargeRequestId y con otro contenido")),
            // Unicidad de Refund.refundRequestId → el error que el diseño NOMBRA para ella
            // (naturalKeyError / indexes[].error). No se deduce: lo dijo el diseño.
            Map.entry("uk_refunds_natural", () -> new RefundRequestConflictError(
                    "Ya existe una devolución con ese refundRequestId con otro importe o sobre otro cobro")),
            // Unicidad de Wallet.customerRef → el error que el diseño NOMBRA para ella
            // (naturalKeyError / indexes[].error). No se deduce: lo dijo el diseño.
            Map.entry("uk_wallets_natural", () -> new ConcurrentModificationError(
                    "El cobro cambió mientras se procesaba la petición")),
            // Unicidad de PaymentMethod.wallet (isDefault = true) → el error que el diseño NOMBRA para ella
            // (naturalKeyError / indexes[].error). No se deduce: lo dijo el diseño.
            Map.entry("uk_payment_methods_wallet", () -> new ConcurrentModificationError(
                    "El cobro cambió mientras se procesaba la petición")));

    // ── Validación ───────────────────────────────────────────────────────────
    // Errores de FORMA de la petición: 400. El 422 queda para las reglas de
    // negocio tipadas que declara use-cases.keel.yaml (BusinessException), que
    // es la distinción que esperan los escenarios de validación.

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ErrorResponse onMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        List<String> details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .toList();
        return new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Validation Error",
                "VALIDATION_ERROR", "La petición no supera las validaciones", details);
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(ConstraintViolationException.class)
    public ErrorResponse onConstraintViolation(ConstraintViolationException exception) {
        List<String> details = exception.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                .toList();
        return new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Validation Error",
                "VALIDATION_ERROR", "La petición viola restricciones declaradas", details);
    }

    // Desde Spring 6.1 la validación de los PARÁMETROS del método (@RequestParam, @RequestPart,
    // el elemento de una lista) la hace el propio MVC y no el proxy de @Validated, así que sale
    // como HandlerMethodValidationException y no como ConstraintViolationException. Sin este
    // handler caía en el catch-all: una etiqueta de 41 caracteres en una subida multipart
    // respondía 500 (corrida asset-vault de keel-spring).
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ErrorResponse onHandlerMethodValidation(HandlerMethodValidationException exception) {
        List<String> details = exception.getAllErrors().stream()
                .map(error -> String.valueOf(error.getDefaultMessage()))
                .toList();
        return new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Validation Error",
                "VALIDATION_ERROR", "La petición viola restricciones declaradas", details);
    }

    // ── Errores de framework ─────────────────────────────────────────────────

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ErrorResponse onMalformedRequest(Exception exception) {
        return new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Bad Request",
                "VALIDATION_ERROR", "Petición malformada", null);
    }

    // Un @RequestParam obligatorio que no viaja en la query lo rechaza Spring
    // ANTES de Bean Validation, así que ningún @NotBlank del parámetro llega a
    // evaluarse: sin este handler el caso "parámetro ausente" cae en el catch-all
    // y devuelve 500 donde el contrato espera 400.
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ErrorResponse onMissingRequestParameter(MissingServletRequestParameterException exception) {
        return new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Bad Request",
                "VALIDATION_ERROR",
                "Falta el parámetro '" + exception.getParameterName() + "' en la petición", null);
    }

    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ErrorResponse onMethodNotAllowed(HttpRequestMethodNotSupportedException exception) {
        return new ErrorResponse(HttpStatus.METHOD_NOT_ALLOWED.value(), "Method Not Allowed", "Método HTTP no soportado");
    }

    // Un camino que no casa con ninguna ruta: Spring lo lanza como NoResourceFoundException, y sin este handler el
    // catch-all lo devolvía como 500 a todo cliente autenticado (sin credencial, la cadena de seguridad ya da 401).
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(NoResourceFoundException.class)
    public ErrorResponse onNoResource(NoResourceFoundException exception) {
        return new ErrorResponse(HttpStatus.NOT_FOUND.value(), "Not Found", "Recurso no encontrado");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> onDataIntegrityViolation(DataIntegrityViolationException exception) {
        DomainException translated = translateConstraint(exception);
        if (translated != null) {
            return onDomainException(translated);
        }
        // Sin la excepción: su mensaje lleva el del motor, y con datos personales (DSL 2.28) el valor que chocó.
        log.warn("Violación de integridad no asociada a ninguna constraint conocida ({})", exception.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(HttpStatus.CONFLICT.value(),
                "Conflict", "Violación de integridad de datos: alguna restricción no se cumplió"));
    }

    /**
     * Busca el nombre de alguna constraint conocida en el mensaje del driver.
     * Se mira la causa más específica porque es la que trae el texto del motor;
     * el mensaje de Spring solo lo envuelve.
     */
    private static DomainException translateConstraint(DataIntegrityViolationException exception) {
        String detail = String.valueOf(exception.getMostSpecificCause().getMessage())
                .concat(" ")
                .concat(String.valueOf(exception.getMessage()))
                .toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Supplier<DomainException>> candidate : CONSTRAINT_TO_ERROR.entrySet()) {
            if (detail.contains(candidate.getKey())) {
                return candidate.getValue().get();
            }
        }
        return null;
    }

    // El diseño declara 'CONCURRENT_MODIFICATION' para este conflicto y ese code viaja en la
    // respuesta pública: es contrato con el integrador, así que gana al genérico del
    // scaffolding. Se delega en onDomainException para que status y forma del cuerpo
    // salgan de la misma metadata que el resto de errores declarados.
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> onOptimisticLockingFailure(ObjectOptimisticLockingFailureException exception) {
        return onDomainException(new ConcurrentModificationError("El recurso fue modificado por otra operación concurrente; reintenta con el estado actual"));
    }

    // Tiempo agotado: la transacción pasó del tope (spring.transaction.default-timeout) y el
    // motor canceló la sentencia, o ya no quedaba tiempo para lanzarla. Es transitorio y la
    // transacción revirtió entera: 503 con Retry-After, no el 500 del catch-all. Sin pila: el
    // motivo va en el mensaje, y una ráfaga de esperas de bloqueo llenaría el log de trazas iguales.
    @ExceptionHandler({ QueryTimeoutException.class, TransactionTimedOutException.class })
    public ResponseEntity<ErrorResponse> onTransactionTimeout(RuntimeException exception) {
        log.warn("Transacción cancelada por tiempo: {}", exception.getMessage());
        HttpStatus status = HttpStatus.valueOf(503);
        return ResponseEntity.status(status)
                .header(HttpHeaders.RETRY_AFTER, "1")
                .body(new ErrorResponse(status.value(), status.getReasonPhrase(), "TRANSACTION_TIMEOUT",
                        "La operación no terminó a tiempo; reinténtala", List.of()));
    }

    // ?sort= fuera de sortable y la lista de la query vacía o ilegible (DSL 2.32): 400 con VALIDATION_ERROR.
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(QueryParameters.InvalidQuery.class)
    public ErrorResponse onInvalidQuery(QueryParameters.InvalidQuery exception) {
        return new ErrorResponse(HttpStatus.BAD_REQUEST.value(), exception.error(),
                "VALIDATION_ERROR", exception.getMessage(),
                exception.parameter() != null ? List.of(exception.parameter()) : null);
    }

    // ── Errores de dominio (jerarquía DomainException) ───────────────────────

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(BadRequestException.class)
    public ErrorResponse onBadRequest(BadRequestException exception) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Bad Request", exception, "Petición inválida");
    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ExceptionHandler(UnauthorizedException.class)
    public ErrorResponse onUnauthorized(UnauthorizedException exception) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "Unauthorized", exception, "Autenticación requerida");
    }

    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ExceptionHandler(ForbiddenException.class)
    public ErrorResponse onForbidden(ForbiddenException exception) {
        return buildResponse(HttpStatus.FORBIDDEN, "Forbidden", exception, "Acceso denegado");
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(NotFoundException.class)
    public ErrorResponse onNotFound(NotFoundException exception) {
        return buildResponse(HttpStatus.NOT_FOUND, "Not Found", exception, "Recurso no encontrado");
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(ConflictException.class)
    public ErrorResponse onConflict(ConflictException exception) {
        return buildResponse(HttpStatus.CONFLICT, "Conflict", exception, "Conflicto con el estado actual del recurso");
    }

    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    @ExceptionHandler(PayloadTooLargeException.class)
    public ErrorResponse onPayloadTooLarge(PayloadTooLargeException exception) {
        return buildResponse(HttpStatus.PAYLOAD_TOO_LARGE, "Payload Too Large", exception,
                "El contenido enviado supera el tamaño permitido");
    }

    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    @ExceptionHandler(BusinessException.class)
    public ErrorResponse onBusinessException(BusinessException exception) {
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, "Business Rule Violation", exception,
                "Se violó una regla de negocio");
    }

    // Errores con status extendido (402, 429, 503…): extienden DomainException
    // directamente y llevan el httpStatus en la metadata.
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> onDomainException(DomainException exception) {
        Integer status = exception.getHttpStatus();
        HttpStatus http = status != null ? HttpStatus.valueOf(status) : HttpStatus.UNPROCESSABLE_ENTITY;
        ErrorResponse body = new ErrorResponse(http.value(), http.getReasonPhrase(), exception.getCode(),
                exception.getMessage() != null ? exception.getMessage() : http.getReasonPhrase(),
                exception.getDetails());
        return ResponseEntity.status(http).body(body);
    }

    // ── Catch-all ────────────────────────────────────────────────────────────

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public ErrorResponse onServerError(Exception exception) {
        log.error("Excepción no controlada", exception);
        return new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error",
                "Ocurrió un error inesperado");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static ErrorResponse buildResponse(HttpStatus status, String error, DomainException exception, String fallbackMessage) {
        String message = exception.getMessage() != null ? exception.getMessage() : fallbackMessage;
        return new ErrorResponse(status.value(), error, exception.getCode(), message, exception.getDetails());
    }
}
