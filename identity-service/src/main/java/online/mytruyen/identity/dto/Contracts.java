package online.mytruyen.identity.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class Contracts {
    private Contracts() {}
    public record Register(@NotBlank @Email @Size(max=254) String email,
            @Pattern(regexp="[a-zA-Z0-9_.-]{3,50}") String username,
            @NotBlank @Size(min=8,max=72) String password) {}
    public record Login(@NotBlank @Email @Size(max=254) String email,
            @NotBlank @Size(max=72) String password) {}
    public record Refresh(@NotBlank @Size(max=128) String refresh_token) {}
    public record Profile(@Pattern(regexp="[a-zA-Z0-9_.-]{3,50}") String username,
            @Size(max=255) String full_name) {}
    public record Password(@NotBlank @Size(max=72) String current_password,
            @NotBlank @Size(min=8,max=72) String new_password) {}
    public record AdminCreate(@NotBlank @Email @Size(max=254) String email,
            @Pattern(regexp="[a-zA-Z0-9_.-]{3,50}") String username,
            @NotBlank @Size(min=8,max=72) String password,
            @NotEmpty List<@Min(1) @Max(2) Integer> roles) {}
    public record AdminUpdate(Boolean is_active, @Size(max=255) String full_name,
            @Size(min=1,max=2) List<@Min(1) @Max(2) Integer> roles) {}
    public record UserView(UUID id, String email, String username, String full_name,
            boolean is_active, List<String> roles, Instant created_at, Instant updated_at) {}
    public record Token(String access_token, String refresh_token, String token_type, long expires_in) {}
    public record Envelope<T>(int status_code, boolean success, String message, T data) {
        public static <T> Envelope<T> ok(int code, T data) {
            return new Envelope<>(code, true, "Success", data);
        }
    }
    public record Pagination(int page, int size, long total_items, long total_pages) {}
    public record UserPage(int status_code, boolean success, String message,
            List<UserView> data, Pagination pagination) {}
}
