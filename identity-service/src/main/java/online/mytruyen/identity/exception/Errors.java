package online.mytruyen.identity.exception;

import online.mytruyen.identity.dto.Contracts;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class Errors {
    @ExceptionHandler(ApiError.class)
    ResponseEntity<Contracts.Envelope<Void>> domain(ApiError e) { return error(e.status, e.getMessage()); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Contracts.Envelope<Void>> conflict() { return error(409, "Email or username already exists, or data conflicts"); }
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<Contracts.Envelope<Void>> staleWrite() { return error(409, "Account changed concurrently; reload and retry"); }
    @ExceptionHandler({MethodArgumentNotValidException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    ResponseEntity<Contracts.Envelope<Void>> invalid() { return error(400, "Invalid request"); }
    private ResponseEntity<Contracts.Envelope<Void>> error(int status, String message) {
        return ResponseEntity.status(status).body(new Contracts.Envelope<>(status, false, message, null));
    }
}
