package online.mytruyen.identity.repository;

import jakarta.persistence.LockModeType;
import online.mytruyen.identity.domain.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface RoleRepository extends JpaRepository<RoleEntity, Short> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from IdentityRole r where r.id = 2")
    RoleEntity lockAdminRole();
}
