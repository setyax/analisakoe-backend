package id.analisakoe.core.service;

import id.analisakoe.context.ContextService;
import id.analisakoe.core.dto.PermissionResponse;
import org.springframework.beans.factory.annotation.Autowired;
import id.analisakoe.core.dto.RoleResponse;
import id.analisakoe.core.repository.PermissionRepository;
import id.analisakoe.core.repository.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RoleService {

    @Autowired
    private ContextService context;

    private final RoleRepository roleRepo;
    private final PermissionRepository permissionRepo;

    public RoleService(RoleRepository roleRepo, PermissionRepository permissionRepo) {
        this.roleRepo = roleRepo;
        this.permissionRepo = permissionRepo;
    }

    private Long tenant() { return context.tenantId(); }

    @Transactional(readOnly = true)
    public List<RoleResponse> list() {
        return roleRepo.findByTenantId(tenant()).stream().map(RoleResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> listPermissions() {
        return permissionRepo.findAllByOrderByModuleAscCodeAsc().stream()
            .map(PermissionResponse::from).toList();
    }
}