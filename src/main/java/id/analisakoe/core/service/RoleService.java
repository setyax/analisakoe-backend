package id.analisakoe.core.service;

import id.analisakoe.core.dto.PermissionResponse;
import id.analisakoe.core.dto.RoleResponse;
import id.analisakoe.core.repository.PermissionRepository;
import id.analisakoe.core.repository.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RoleService {

    private static final Long TENANT = 1L;

    private final RoleRepository roleRepo;
    private final PermissionRepository permissionRepo;

    public RoleService(RoleRepository roleRepo, PermissionRepository permissionRepo) {
        this.roleRepo = roleRepo;
        this.permissionRepo = permissionRepo;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> list() {
        return roleRepo.findByTenantId(TENANT).stream().map(RoleResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> listPermissions() {
        return permissionRepo.findAllByOrderByModuleAscCodeAsc().stream()
            .map(PermissionResponse::from).toList();
    }
}