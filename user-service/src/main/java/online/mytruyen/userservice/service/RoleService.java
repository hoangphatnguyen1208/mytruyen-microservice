package online.mytruyen.userservice.service;

import lombok.RequiredArgsConstructor;
import online.mytruyen.userservice.entity.RoleEntity;
import online.mytruyen.userservice.exception.RoleNotFoundException;
import online.mytruyen.userservice.repository.RoleRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;

    public RoleEntity getRoleByName(String name) {
        return roleRepository.findByName(name)
                .orElseThrow(() -> new RoleNotFoundException("Role not found with name: " + name));
    }

    public RoleEntity getRoleById(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Role not found with id: " + id));
    }

    public RoleEntity save(RoleEntity role) {
        return roleRepository.save(role);
    }
}
