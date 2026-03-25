package online.mytruyen.userservice.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.AllArgsConstructor;
import online.mytruyen.userservice.common.PageResponse;
import online.mytruyen.userservice.common.Pagination;
import online.mytruyen.userservice.common.Response;
import online.mytruyen.userservice.dto.UserPublic;
import online.mytruyen.userservice.dto.UserCreate;
import online.mytruyen.userservice.dto.UserUpdate;
import online.mytruyen.userservice.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/users")
@AllArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping
    @SecurityRequirement(name = "bearerAuth")
    public PageResponse<List<UserPublic>> getUsers(Pageable pageable) {
        Page<UserPublic> pageData = userService.getUsers(pageable);
        Pagination pagination = new Pagination(pageData.getNumber(), pageData.getSize(), pageData.getTotalElements(), pageData.getTotalPages());
        return PageResponse.success(200, pageData.getContent(), pagination);
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    public Response<UserPublic> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        UserPublic user = userService.getUserByUsername(userDetails.getUsername());
        return Response.success(200, user);
    }

    @PostMapping("/create")
    @SecurityRequirement(name = "bearerAuth")
    public Response<UserPublic> create(@RequestBody UserCreate userCreate) {
        UserPublic user = userService.save(userCreate);
        return Response.success(201, user);
    }

    @PatchMapping("/me/update")
    @SecurityRequirement(name = "bearerAuth")
    public Response<UserPublic> updateCurrentUser(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody UserUpdate userUpdate
    ) {
        UserPublic user = userService.updateMe(userUpdate, userDetails);
        return Response.success(200, user);
    }

    @DeleteMapping("/me/delete")
    @SecurityRequirement(name = "bearerAuth")
    public Response<String> deleteMe(@AuthenticationPrincipal UserDetails userDetails) {
        userService.deleteMe(userDetails);
        return Response.success(200, "User deleted successfully");
    }
}
