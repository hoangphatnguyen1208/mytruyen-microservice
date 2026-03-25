package online.mytruyen.userservice.repository;

import online.mytruyen.userservice.entity.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<RoleEntity, Long> {
     RoleEntity findByName(String name);
}
