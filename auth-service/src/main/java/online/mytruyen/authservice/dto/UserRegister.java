package online.mytruyen.authservice.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRegister {
    private String email;
    private String password;
}
