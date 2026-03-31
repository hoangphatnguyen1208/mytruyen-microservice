package online.mytruyen.userservice.exception;

import online.mytruyen.userservice.common.Response;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Response<String>> handleUserNotFoundException(UserNotFoundException ex) {
        return ResponseEntity.status(404).body(Response.error(404, ex.getMessage()));
    }

    @ExceptionHandler(RoleNotFoundException.class)
    public ResponseEntity<Response<String>> handleRoleNotFoundException(RoleNotFoundException ex) {
        return ResponseEntity.status(404).body(Response.error(404, ex.getMessage()));
    }
}
