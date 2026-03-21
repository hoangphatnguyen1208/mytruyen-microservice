package online.mytruyen.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserBase {
    @UUID
    private String id;

    @Email
    @NotBlank
    private String email;

    private String full_name;

    private Boolean is_active;
}
