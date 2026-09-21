package online.mytruyen.userservice.controller;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import online.mytruyen.userservice.common.Response;
import online.mytruyen.userservice.dto.UserInternal;
import online.mytruyen.userservice.dto.UserPublic;
import online.mytruyen.userservice.dto.UserRegister;
import online.mytruyen.userservice.entity.UserEntity;
import online.mytruyen.userservice.exception.UserNotFoundException;
import online.mytruyen.userservice.repository.UserRepository;
import online.mytruyen.userservice.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal/users")
@AllArgsConstructor
public class InternalController {
    private final UserRepository userRepository;
    private final UserService userService;

    @GetMapping("/by-username/{username}")
    public ResponseEntity<Response<UserInternal>> getUserByUsername(@PathVariable String username) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        return ResponseEntity.ok(Response.success(200, new UserInternal(user)));
    }

    @GetMapping("/by-id/{id}")
    public ResponseEntity<Response<UserInternal>> getUserById(@PathVariable String id) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        return ResponseEntity.ok(Response.success(200, new UserInternal(user)));
    }

    @GetMapping("/by-email/{email}")
    public ResponseEntity<Response<UserInternal>> getUserByEmail(@PathVariable String email) {
        UserEntity user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        return ResponseEntity.ok(Response.success(200, new UserInternal(user)));
    }

    @PostMapping("/register")
    public ResponseEntity<Response<UserPublic>> register(@Valid @RequestBody UserRegister userRegister) {
        UserPublic user = userService.save(userRegister);
        return ResponseEntity.status(HttpStatus.CREATED).body(Response.success(201, user));
    }
}
