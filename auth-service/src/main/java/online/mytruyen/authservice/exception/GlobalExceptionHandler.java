package online.mytruyen.authservice.exception;

import online.mytruyen.authservice.common.Response;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Response<String>> handleUserNotFoundException(UnauthorizedException ex) {
        return ResponseEntity.status(401).body(Response.error(401, ex.getMessage()));
    }
}
