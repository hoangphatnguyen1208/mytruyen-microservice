package online.mytruyen.identity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.*;

@Entity(name = "IdentityUser")
@Table(name = "users")
@Getter @Setter @NoArgsConstructor
public class UserEntity {
    @Id private UUID id;
    @Column(nullable = false, length = 254) private String email;
    @Column(length = 50) private String username;
    @Column(name = "full_name") private String fullName;
    @Column(name = "is_active", nullable = false) private boolean active = true;
    @Column(name = "deleted_at") private Instant deletedAt;
    @Version private Long version;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id"))
    @org.hibernate.annotations.BatchSize(size = 100)
    private Set<RoleEntity> roles = new LinkedHashSet<>();

    @PrePersist void initialize() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = createdAt;
    }
}
