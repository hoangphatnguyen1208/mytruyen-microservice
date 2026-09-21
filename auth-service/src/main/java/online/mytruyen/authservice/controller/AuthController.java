package online.mytruyen.authservice.controller;

import lombok.RequiredArgsConstructor;
import online.mytruyen.authservice.common.Response;
import online.mytruyen.authservice.dto.Token;
import online.mytruyen.authservice.dto.UserLogin;
import online.mytruyen.authservice.dto.UserPublic;
import online.mytruyen.authservice.dto.UserRegister;
import online.mytruyen.authservice.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<Response<Token>> login(@RequestBody UserLogin userLogin) {
        return ResponseEntity.ok(
                Response.success(200, new Token(authService.login(userLogin), "bearer"))
        );
    }

    @PostMapping("/register")
    public ResponseEntity<Response<UserPublic>> register(@RequestBody UserRegister userRegister) {
        UserPublic user = authService.register(userRegister);
        return ResponseEntity.ok(Response.success(201, user));
    }
}
