package id.analisakoe.web.ui;

import id.analisakoe.context.ContextService;

import id.analisakoe.core.menu.MenuService;
import id.analisakoe.core.entity.Permission;
import id.analisakoe.core.entity.Role;
import id.analisakoe.core.repository.PermissionRepository;
import id.analisakoe.core.repository.RoleRepository;
import id.analisakoe.web.ui.SessionInterceptor;
import id.analisakoe.web.ui.SessionUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;
import java.util.stream.Collectors;

/**
 * CRUD Role & Permission (master hak akses).
 * Role disimpan di core.role, mapping permission di core.role_permission.
 */
@Controller
@RequestMapping("/ui")
public class UiRoleController {

    @Autowired
    private ContextService context;

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final RoleRepository roleRepo;
    private final PermissionRepository permissionRepo;
    private final MenuService menuService;

    public UiRoleController(RoleRepository roleRepo,
                            PermissionRepository permissionRepo,
                            MenuService menuService) {
        this.roleRepo = roleRepo;
        this.permissionRepo = permissionRepo;
        this.menuService = menuService;
    }

    // ==================== LIST ====================
    @GetMapping("/roles")
    public String listRoles(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Roles");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        List<Map<String, Object>> roles = new ArrayList<>();
        for (Role r : roleRepo.findByTenantId(context.tenantId())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("code", r.getCode());
            m.put("name", r.getName());
            m.put("description", r.getDescription());
            m.put("permCount", r.getPermissions().size());
            long userCount = ((Number) em.createNativeQuery(
                    "SELECT COUNT(*) FROM core.user_role WHERE role_id = :id")
                    .setParameter("id", r.getId()).getSingleResult()).longValue();
            m.put("userCount", userCount);
            roles.add(m);
        }
        model.addAttribute("roles", roles);
        return "ui/roles/list";
    }

    // ==================== FORM ====================
    @GetMapping("/roles/new")
    public String newForm(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "New Role");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("isNew", true);
        prepareFormData(model, null);
        return "ui/roles/form";
    }

    @GetMapping("/roles/{id}/edit")
    public String editForm(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Edit Role");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("isNew", false);
        try {
            prepareFormData(model, id);
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Role tidak ditemukan.");
            return "redirect:/ui/roles";
        }
        return "ui/roles/form";
    }

    private void prepareFormData(Model model, Long roleId) {
        // Permission dikelompokkan per module untuk checkbox yang rapi
        Map<String, List<Permission>> byModule = permissionRepo.findAllByOrderByModuleAscCodeAsc()
                .stream().collect(Collectors.groupingBy(Permission::getModule, LinkedHashMap::new, Collectors.toList()));
        model.addAttribute("permGroups", byModule);

        Set<Long> selected = new HashSet<>();
        Role role = null;
        if (roleId != null) {
            role = roleRepo.findById(roleId).orElseThrow(() -> new IllegalArgumentException("Role tidak ditemukan"));
            selected = role.getPermissions().stream().map(Permission::getId).collect(Collectors.toSet());
        }
        model.addAttribute("role", role);
        model.addAttribute("selectedPermIds", selected);
    }

    // ==================== SAVE ====================
    @PostMapping("/roles/save")
    public String save(@RequestParam(required = false) Long id,
                       @RequestParam String code,
                       @RequestParam String name,
                       @RequestParam(required = false) String description,
                       @RequestParam(required = false) List<Long> permissionIds,
                       RedirectAttributes ra) {
        try {
            transactionTemplate.executeWithoutResult(tx -> {
                Role role;
                if (id == null) {
                    String cleanCode = code.trim().toUpperCase().replace(' ', '_');
                    if (roleRepo.existsByTenantIdAndCode(context.tenantId(), cleanCode)) {
                        throw new IllegalArgumentException("Kode role \"" + cleanCode + "\" sudah dipakai role lain.");
                    }
                    role = new Role();
                    role.setTenantId(context.tenantId());
                    role.setCode(cleanCode);
                } else {
                    role = roleRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("Role tidak ditemukan"));
                    String cleanCode = code.trim().toUpperCase().replace(' ', '_');
                    if (!role.getCode().equals(cleanCode)
                            && roleRepo.existsByTenantIdAndCode(context.tenantId(), cleanCode)) {
                        throw new IllegalArgumentException("Kode role \"" + cleanCode + "\" sudah dipakai role lain.");
                    }
                    role.setCode(cleanCode);
                }
                role.setName(name.trim());
                role.setDescription(description);

                // Sinkronkan permission
                role.getPermissions().clear();
                if (permissionIds != null && !permissionIds.isEmpty()) {
                    List<Permission> perms = permissionRepo.findAllById(permissionIds);
                    role.getPermissions().addAll(perms);
                }
                roleRepo.save(role);
            });
            ra.addFlashAttribute("success", "Role \"" + name.trim() + "\" berhasil disimpan.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal menyimpan role: " + rootMessage(e));
        }
        return "redirect:/ui/roles";
    }

    // ==================== DELETE ====================
    @PostMapping("/roles/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            transactionTemplate.executeWithoutResult(tx -> {
                Role role = roleRepo.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("Role tidak ditemukan"));

                long userCount = ((Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM core.user_role WHERE role_id = :id")
                        .setParameter("id", id).getSingleResult()).longValue();
                if (userCount > 0) {
                    throw new IllegalStateException(
                        "Tidak bisa dihapus. Role ini masih dipakai " + userCount + " user. " +
                        "Lepaskan role dari user terlebih dahulu di User Management.");
                }

                roleRepo.delete(role);
            });
            ra.addFlashAttribute("success", "Role berhasil dihapus.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal hapus role: " + rootMessage(e));
        }
        return "redirect:/ui/roles";
    }

    // ==================== PERMISSIONS (read-only list) ====================
    @GetMapping("/permissions")
    public String listPermissions(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Permissions");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        Map<String, List<id.analisakoe.core.dto.PermissionResponse>> grouped =
                permissionRepo.findAllByOrderByModuleAscCodeAsc().stream()
                        .map(id.analisakoe.core.dto.PermissionResponse::from)
                        .collect(Collectors.groupingBy(
                                id.analisakoe.core.dto.PermissionResponse::module,
                                LinkedHashMap::new, Collectors.toList()));
        model.addAttribute("permGrouped", grouped);
        return "ui/permissions/list";
    }

    private String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
        return cur.getMessage() != null ? cur.getMessage() : cur.toString();
    }
}
