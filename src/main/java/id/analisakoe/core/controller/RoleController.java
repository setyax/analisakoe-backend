package id.analisakoe.core.controller;

import id.analisakoe.core.dto.PermissionResponse;
import id.analisakoe.core.dto.RoleResponse;
import id.analisakoe.core.service.RoleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/core")
public class RoleController {

    private final RoleService service;

    public RoleController(RoleService service) { this.service = service; }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('core.role.read') or hasRole('ADMIN')")
    public List<RoleResponse> listRoles() {
        return service.list();
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('core.role.read') or hasRole('ADMIN')")
    public List<PermissionResponse> listPermissions() {
        return service.listPermissions();
    }
}