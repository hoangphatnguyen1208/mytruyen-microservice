package online.mytruyen.authservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRegister {
    @NotBlank
    private String email;

    @NotBlank
    private String password;
}
