package online.mytruyen.catalog.exception;

import online.mytruyen.catalog.dto.ApiResponses;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.*;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<?> domain(ApiException e) { return error(e.status, e.getMessage()); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> conflict() { return error(409, "Duplicate slug or resource still referenced"); }
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<?> stale() { return error(409, "Resource changed; reload and retry"); }
    @ExceptionHandler({MethodArgumentNotValidException.class, MethodArgumentTypeMismatchException.class,
        HttpMessageNotReadableException.class, ConstraintViolationException.class})
    ResponseEntity<?> invalid() { return error(400, "Invalid request"); }
    private ResponseEntity<?> error(int status, String message) {
        return ResponseEntity.status(status).body(new ApiResponses.Response<>(status, false, message, null));
    }
}
