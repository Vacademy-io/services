package vacademy.io.auth_service.feature.user.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import vacademy.io.auth_service.feature.user.dto.RoleDTO;
import vacademy.io.auth_service.feature.user.repository.RoleRepository;
import vacademy.io.common.auth.entity.UserRole;
import vacademy.io.common.exceptions.UserNotFoundException;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RoleService {

    @Autowired
    RoleRepository roleRepository;

    public List<RoleDTO> getRolesByUserId(String userId) {

        if(!ifUserExist(userId)) {
            throw new UserNotFoundException("User with Id "+ userId + " not found");
        }
        List<UserRole> roles = roleRepository.findRolesByUserId(userId);

        return roles.stream()
                .map(role -> new RoleDTO(role.getId(), role.getName()
                ))
                .collect(Collectors.toList());
    }

    public boolean ifUserExist(String userId) {

        return roleRepository.existsByUserId(userId);
    }

}
