package online.mytruyen.userservice.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.AllArgsConstructor;
import online.mytruyen.userservice.common.PageResponse;
import online.mytruyen.userservice.common.Pagination;
import online.mytruyen.userservice.common.Response;
import online.mytruyen.userservice.dto.UserCreate;
import online.mytruyen.userservice.dto.UserPublic;
import online.mytruyen.userservice.dto.UserRegister;
import online.mytruyen.userservice.dto.UserUpdate;
import online.mytruyen.userservice.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;


@RestController
@RequestMapping("/api/v1/users")
@AllArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<PageResponse<List<UserPublic>>> getUsers(Pageable pageable) {
        Page<UserPublic> pageData = userService.getUsers(pageable);
        Pagination pagination = new Pagination(pageData.getNumber(), pageData.getSize(), pageData.getTotalElements(), pageData.getTotalPages());
        return ResponseEntity.ok(PageResponse.success(200, pageData.getContent(), pagination));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<?> create(@RequestBody UserCreate userCreate) {
        UserPublic user = userService.save(userCreate);
        URI uri = URI.create("/v1/users/" + user.getId());
        return ResponseEntity.created(uri).body(Response.success(201, user));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody UserRegister userRegister) {
        UserPublic user = userService.save(userRegister);
        URI uri = URI.create("/v1/users/" + user.getId());
        return ResponseEntity.created(uri).body(Response.success(201, user));
    }

    @GetMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Response<UserPublic>> getUserById(@PathVariable String id) {
        UserPublic user = userService.getUserById(id);
        return ResponseEntity.ok(Response.success(200, user));
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Response<UserPublic>> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        UserPublic user = userService.getUserById(userDetails.getUsername());
        return ResponseEntity.ok(Response.success(200, user));
    }

    @PatchMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Response<UserPublic>> updateCurrentUser(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody UserUpdate userUpdate
    ) {
        UserPublic user = userService.updateMe(userUpdate, userDetails);
        return ResponseEntity.ok(Response.success(200, user));
    }

    @DeleteMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<?> deleteMe(@AuthenticationPrincipal UserDetails userDetails) {
        userService.deleteMe(userDetails);
        return ResponseEntity.noContent().build();
    }
}
