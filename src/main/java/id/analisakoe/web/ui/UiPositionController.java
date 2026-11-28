package id.analisakoe.web.ui;

import org.springframework.beans.factory.annotation.Autowired;

import id.analisakoe.context.ContextService;

import id.analisakoe.core.menu.MenuService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/ui/master/positions")
public class UiPositionController {

    @Autowired
    private ContextService context;

    @PersistenceContext
    private EntityManager em;

    private final MenuService menuService;

    public UiPositionController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping
    public String list(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Positions");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        List<Object[]> items = em.createNativeQuery(
            "SELECT p.id, p.code, p.name, p.level, COALESCE(d.name,'-') AS dept_name, p.min_salary, p.max_salary, p.is_active " +
            "FROM hr.position p LEFT JOIN hr.department d ON p.department_id = d.id ORDER BY p.level, p.code").getResultList();
        model.addAttribute("items", items);
        return "ui/master/position_list";
    }

    @GetMapping("/new")
    public String newForm(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "New Position");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("isNew", true);
        model.addAttribute("departments", em.createNativeQuery("SELECT id, code, name FROM hr.department ORDER BY code").getResultList());
        return "ui/master/position_form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Edit Position");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("isNew", false);
        model.addAttribute("departments", em.createNativeQuery("SELECT id, code, name FROM hr.department ORDER BY code").getResultList());
        try {
            Object[] row = (Object[]) em.createNativeQuery("SELECT id, code, name, level, department_id, min_salary, max_salary, is_active FROM hr.position WHERE id = :id")
                .setParameter("id", id).getSingleResult();
            Map<String, Object> c = new HashMap<>();
            c.put("id", row[0]); c.put("code", row[1]); c.put("name", row[2]); c.put("level", row[3]);
            c.put("departmentId", row[4]); c.put("minSalary", row[5]); c.put("maxSalary", row[6]); c.put("isActive", row[7]);
            model.addAttribute("c", c);
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Data tidak ditemukan.");
            return "redirect:/ui/master/positions";
        }
        return "ui/master/position_form";
    }

    @Transactional
    @PostMapping("/save")
    public String save(@RequestParam(required = false) Long id,
                       @RequestParam String code, @RequestParam String name,
                       @RequestParam(defaultValue = "1") Integer level,
                       @RequestParam(required = false) Long departmentId,
                       @RequestParam(required = false) BigDecimal minSalary,
                       @RequestParam(required = false) BigDecimal maxSalary,
                       @RequestParam(defaultValue = "true") Boolean isActive,
                       HttpSession session,
                       RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        final Long tenantId = context.tenantId();
        final Long compId = context.companyId();
        try {
            if (id == null) {
                em.createNativeQuery("INSERT INTO hr.position (tenant_id, company_id, code, name, level, department_id, min_salary, max_salary, is_active) VALUES (:tenantId, :compId, :code, :name, :lvl, :dept, :min, :max, :active)")
                  .setParameter("tenantId", tenantId).setParameter("compId", compId)
                  .setParameter("code", code).setParameter("name", name).setParameter("lvl", level)
                  .setParameter("dept", departmentId).setParameter("min", minSalary).setParameter("max", maxSalary)
                  .setParameter("active", isActive).executeUpdate();
                ra.addFlashAttribute("success", "Position berhasil ditambahkan.");
            } else {
                em.createNativeQuery("UPDATE hr.position SET code=:code, name=:name, level=:lvl, department_id=:dept, min_salary=:min, max_salary=:max, is_active=:active WHERE id=:id")
                  .setParameter("code", code).setParameter("name", name).setParameter("lvl", level)
                  .setParameter("dept", departmentId).setParameter("min", minSalary).setParameter("max", maxSalary)
                  .setParameter("active", isActive).setParameter("id", id).executeUpdate();
                ra.addFlashAttribute("success", "Position berhasil diupdate.");
            }
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal: " + e.getMessage());
        }
        return "redirect:/ui/master/positions";
    }

    @Transactional
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            em.createNativeQuery("DELETE FROM hr.position WHERE id = :id").setParameter("id", id).executeUpdate();
            ra.addFlashAttribute("success", "Position berhasil dihapus.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal hapus: " + e.getMessage());
        }
        return "redirect:/ui/master/positions";
    }
}