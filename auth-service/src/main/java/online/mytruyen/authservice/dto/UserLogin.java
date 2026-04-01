package online.mytruyen.authservice.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserLogin {
    private String email;
    private String password;
}
