package online.mytruyen.authservice.dto;

import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserInternal {
    private String id;
    private String username;
    private String hashedPassword;
    private List<String> roles;
}
