package pt.arrumo.errors;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Error responses are always JSON {"error": "..."} with no internal details (stack traces only go to the log).
 * Messages are shown to users, so they are in Portuguese.
 */
@RestControllerAdvice
public class ErrorHandling {

    private static final Logger log = LoggerFactory.getLogger(ErrorHandling.class);
    private static final String CONCURRENCY =
            "Outra pessoa alterou isto ao mesmo tempo. O mapa foi atualizado; confirma e tenta de novo.";

    @ExceptionHandler(ApiError.class)
    ResponseEntity<Map<String, String>> api(ApiError e) {
        return response(e.getStatus(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> validation(MethodArgumentNotValidException e) {
        var field = e.getBindingResult().getFieldError();
        String msg = field == null ? "Pedido inválido." : field.getField() + ": " + field.getDefaultMessage();
        return response(HttpStatus.BAD_REQUEST, msg);
    }

    /** Real-time connections that timed out or were closed by the browser: normal, nothing to answer. */
    @ExceptionHandler({AsyncRequestTimeoutException.class, AsyncRequestNotUsableException.class})
    void subscriberClosed() {
    }

    @ExceptionHandler({HttpRequestMethodNotSupportedException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class, HttpMediaTypeNotSupportedException.class})
    ResponseEntity<Map<String, String>> badRequest(Exception e) {
        return response(HttpStatus.BAD_REQUEST, "Pedido inválido.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, String>> unreadable(HttpMessageNotReadableException e) {
        return response(HttpStatus.BAD_REQUEST, "Pedido inválido.");
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class,
            DataIntegrityViolationException.class, TransactionSystemException.class})
    ResponseEntity<Map<String, String>> concurrency(Exception e) {
        log.info("Concurrency conflict: {}", e.getMessage());
        return response(HttpStatus.CONFLICT, CONCURRENCY);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Map<String, String>> forbidden(AccessDeniedException e) {
        return response(HttpStatus.FORBIDDEN, "O teu perfil não tem permissão para isto.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Map<String, String>> doesNotExist(NoResourceFoundException e) {
        return response(HttpStatus.NOT_FOUND, "Não existe.");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> unexpected(Exception e) {
        log.error("Unexpected error", e);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno. Tenta de novo; se continuar, avisa o administrador.");
    }

    private static ResponseEntity<Map<String, String>> response(HttpStatus s, String msg) {
        return ResponseEntity.status(s).body(Map.of("error", msg));
    }
}
