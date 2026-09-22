package online.mytruyen.identity;

import org.springframework.dao.DataIntegrityViolationException;
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
    @ExceptionHandler({MethodArgumentNotValidException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    ResponseEntity<Contracts.Envelope<Void>> invalid() { return error(400, "Invalid request"); }
    private ResponseEntity<Contracts.Envelope<Void>> error(int status, String message) {
        return ResponseEntity.status(status).body(new Contracts.Envelope<>(status, false, message, null));
    }
}
