package online.mytruyen.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserCreate {
    @Email
    @NotBlank
    @Size(max = 254)
    private String email;

    @NotBlank
    @Pattern(regexp = "^[a-zA-Z0-9_.-]{3,50}$")
    private String username;

    @NotBlank
    @Size(min = 8, max = 128)
    private String password;

    @NotEmpty
    private List<Long> roles;
}
