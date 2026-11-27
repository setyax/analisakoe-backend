package id.analisakoe.web.ui;

import id.analisakoe.core.menu.MenuService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/ui/master/departments")
public class UiDepartmentController {

    @PersistenceContext
    private EntityManager em;

    private final MenuService menuService;

    public UiDepartmentController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping
    public String list(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Departments");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        List<Object[]> items = em.createNativeQuery(
            "SELECT d.id, d.code, d.name, COALESCE(c.name,'-') AS cost_center_name, d.is_active " +
            "FROM hr.department d LEFT JOIN fin.cost_center c ON d.cost_center_id = c.id ORDER BY d.code").getResultList();
        model.addAttribute("items", items);
        return "ui/master/department_list";
    }

    @GetMapping("/new")
    public String newForm(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "New Department");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("isNew", true);
        model.addAttribute("costCenters", em.createNativeQuery("SELECT id, code, name FROM fin.cost_center ORDER BY code").getResultList());
        return "ui/master/department_form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Edit Department");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("isNew", false);
        model.addAttribute("costCenters", em.createNativeQuery("SELECT id, code, name FROM fin.cost_center ORDER BY code").getResultList());
        try {
            Object[] row = (Object[]) em.createNativeQuery("SELECT id, code, name, cost_center_id, is_active FROM hr.department WHERE id = :id")
                .setParameter("id", id).getSingleResult();
            Map<String, Object> c = new HashMap<>();
            c.put("id", row[0]); c.put("code", row[1]); c.put("name", row[2]); c.put("costCenterId", row[3]); c.put("isActive", row[4]);
            model.addAttribute("c", c);
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Data tidak ditemukan.");
            return "redirect:/ui/master/departments";
        }
        return "ui/master/department_form";
    }

    @Transactional
    @PostMapping("/save")
    public String save(@RequestParam(required = false) Long id,
                       @RequestParam String code, @RequestParam String name,
                       @RequestParam(required = false) Long costCenterId,
                       @RequestParam(defaultValue = "true") Boolean isActive,
                       HttpSession session,
                       RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        final Long tenantId = (user != null && user.getTenantId() != null) ? user.getTenantId() : 1L;
        final Long compId = (user != null && user.getActiveCompanyId() != null) ? user.getActiveCompanyId() : 11L;
        try {
            if (id == null) {
                em.createNativeQuery("INSERT INTO hr.department (tenant_id, company_id, code, name, cost_center_id, is_active) VALUES (:tenantId, :compId, :code, :name, :cc, :active)")
                  .setParameter("tenantId", tenantId).setParameter("compId", compId)
                  .setParameter("code", code).setParameter("name", name).setParameter("cc", costCenterId).setParameter("active", isActive).executeUpdate();
                ra.addFlashAttribute("success", "Department berhasil ditambahkan.");
            } else {
                em.createNativeQuery("UPDATE hr.department SET code=:code, name=:name, cost_center_id=:cc, is_active=:active WHERE id=:id")
                  .setParameter("code", code).setParameter("name", name).setParameter("cc", costCenterId).setParameter("active", isActive).setParameter("id", id).executeUpdate();
                ra.addFlashAttribute("success", "Department berhasil diupdate.");
            }
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal: " + e.getMessage());
        }
        return "redirect:/ui/master/departments";
    }

    @Transactional
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            em.createNativeQuery("DELETE FROM hr.department WHERE id = :id").setParameter("id", id).executeUpdate();
            ra.addFlashAttribute("success", "Department berhasil dihapus.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal hapus: " + e.getMessage());
        }
        return "redirect:/ui/master/departments";
    }
}