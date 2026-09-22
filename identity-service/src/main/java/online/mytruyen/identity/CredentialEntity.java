package online.mytruyen.identity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.*;

@Entity(name = "IdentityCredential")
@Table(name = "user_credentials")
@Getter @Setter @NoArgsConstructor
public class CredentialEntity {
    @Id @Column(name = "user_id") private UUID id;
    @MapsId @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id") private UserEntity user;
    @Column(name = "password_hash", nullable = false, columnDefinition = "text") private String passwordHash;
    @Column(name = "password_changed_at", nullable = false) private Instant passwordChangedAt;
}
