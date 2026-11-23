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
@RequestMapping("/ui/master/cost-centers")
public class UiCostCenterController {

    @PersistenceContext
    private EntityManager em;

    private final MenuService menuService;

    public UiCostCenterController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping
    public String list(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Cost Centers");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        List<Object[]> items = em.createNativeQuery(
            "SELECT c.id, c.code, c.name, COALESCE(p.name,'-') AS parent_name, c.is_active " +
            "FROM fin.cost_center c LEFT JOIN fin.cost_center p ON c.parent_id = p.id ORDER BY c.code").getResultList();
        model.addAttribute("items", items);
        return "ui/master/cost_center_list";
    }

    @GetMapping("/new")
    public String newForm(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "New Cost Center");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("isNew", true);
        model.addAttribute("parents", em.createNativeQuery("SELECT id, code, name FROM fin.cost_center ORDER BY code").getResultList());
        return "ui/master/cost_center_form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Edit Cost Center");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("isNew", false);
        model.addAttribute("parents", em.createNativeQuery("SELECT id, code, name FROM fin.cost_center ORDER BY code").getResultList());
        try {
            Object[] row = (Object[]) em.createNativeQuery("SELECT id, code, name, parent_id, is_active FROM fin.cost_center WHERE id = :id")
                .setParameter("id", id).getSingleResult();
            Map<String, Object> c = new HashMap<>();
            c.put("id", row[0]); c.put("code", row[1]); c.put("name", row[2]); c.put("parentId", row[3]); c.put("isActive", row[4]);
            model.addAttribute("c", c);
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Data tidak ditemukan.");
            return "redirect:/ui/master/cost-centers";
        }
        return "ui/master/cost_center_form";
    }

    @Transactional
    @PostMapping("/save")
    public String save(@RequestParam(required = false) Long id,
                       @RequestParam String code, @RequestParam String name,
                       @RequestParam(required = false) Long parentId,
                       @RequestParam(defaultValue = "true") Boolean isActive,
                       HttpSession session,
                       RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        final Long tenantId = (user != null && user.getTenantId() != null) ? user.getTenantId() : 1L;
        final Long compId = (user != null && user.getActiveCompanyId() != null) ? user.getActiveCompanyId() : 1L;
        try {
            if (id == null) {
                em.createNativeQuery("INSERT INTO fin.cost_center (tenant_id, company_id, code, name, parent_id, is_active) VALUES (:tenantId, :compId, :code, :name, :parent, :active)")
                  .setParameter("tenantId", tenantId).setParameter("compId", compId)
                  .setParameter("code", code).setParameter("name", name).setParameter("parent", parentId).setParameter("active", isActive).executeUpdate();
                ra.addFlashAttribute("success", "Cost Center berhasil ditambahkan.");
            } else {
                em.createNativeQuery("UPDATE fin.cost_center SET code=:code, name=:name, parent_id=:parent, is_active=:active WHERE id=:id")
                  .setParameter("code", code).setParameter("name", name).setParameter("parent", parentId).setParameter("active", isActive).setParameter("id", id).executeUpdate();
                ra.addFlashAttribute("success", "Cost Center berhasil diupdate.");
            }
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal: " + e.getMessage());
        }
        return "redirect:/ui/master/cost-centers";
    }

    @Transactional
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            em.createNativeQuery("DELETE FROM fin.cost_center WHERE id = :id").setParameter("id", id).executeUpdate();
            ra.addFlashAttribute("success", "Cost Center berhasil dihapus.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal hapus: " + e.getMessage());
        }
        return "redirect:/ui/master/cost-centers";
    }
}