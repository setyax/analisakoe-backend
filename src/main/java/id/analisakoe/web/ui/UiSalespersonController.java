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

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/ui/master/salespersons")
public class UiSalespersonController {

    @PersistenceContext
    private EntityManager em;

    private final MenuService menuService;

    public UiSalespersonController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping
    public String list(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Salespersons");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        List<Object[]> items = em.createNativeQuery("SELECT id, code, name, COALESCE(phone,'-'), COALESCE(email,'-'), commission_pct, is_active FROM md.salesperson ORDER BY code").getResultList();
        model.addAttribute("items", items);
        return "ui/master/salesperson_list";
    }

    @GetMapping("/new")
    public String newForm(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "New Salesperson");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("isNew", true);
        model.addAttribute("employees", em.createNativeQuery("SELECT id, nip, name FROM hr.employee WHERE status='ACTIVE' ORDER BY name").getResultList());
        return "ui/master/salesperson_form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Edit Salesperson");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("isNew", false);
        model.addAttribute("employees", em.createNativeQuery("SELECT id, nip, name FROM hr.employee WHERE status='ACTIVE' ORDER BY name").getResultList());
        try {
            Object[] row = (Object[]) em.createNativeQuery("SELECT id, code, name, employee_id, COALESCE(commission_pct,0), COALESCE(phone,''), COALESCE(email,''), is_active FROM md.salesperson WHERE id = :id")
                .setParameter("id", id).getSingleResult();
            Map<String, Object> c = new HashMap<>();
            c.put("id", row[0]); c.put("code", row[1]); c.put("name", row[2]); c.put("employeeId", row[3]);
            c.put("commissionPct", row[4]); c.put("phone", row[5]); c.put("email", row[6]); c.put("isActive", row[7]);
            model.addAttribute("c", c);
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Data tidak ditemukan.");
            return "redirect:/ui/master/salespersons";
        }
        return "ui/master/salesperson_form";
    }

    @Transactional
    @PostMapping("/save")
    public String save(@RequestParam(required = false) Long id,
                       @RequestParam String code, @RequestParam String name,
                       @RequestParam(required = false) Long employeeId,
                       @RequestParam(required = false) BigDecimal commissionPct,
                       @RequestParam(required = false) String phone,
                       @RequestParam(required = false) String email,
                       @RequestParam(defaultValue = "true") Boolean isActive,
                       HttpSession session,
                       RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        final Long tenantId = (user != null && user.getTenantId() != null) ? user.getTenantId() : 1L;
        final Long compId = (user != null && user.getActiveCompanyId() != null) ? user.getActiveCompanyId() : 1L;
        try {
            if (id == null) {
                em.createNativeQuery("INSERT INTO md.salesperson (tenant_id, company_id, code, name, employee_id, commission_pct, phone, email, is_active) VALUES (:tenantId, :compId, :code, :name, :emp, :comm, :phone, :email, :active)")
                  .setParameter("tenantId", tenantId).setParameter("compId", compId)
                  .setParameter("code", code).setParameter("name", name).setParameter("emp", employeeId)
                  .setParameter("comm", commissionPct).setParameter("phone", phone).setParameter("email", email)
                  .setParameter("active", isActive).executeUpdate();
                ra.addFlashAttribute("success", "Salesperson berhasil ditambahkan.");
            } else {
                em.createNativeQuery("UPDATE md.salesperson SET code=:code, name=:name, employee_id=:emp, commission_pct=:comm, phone=:phone, email=:email, is_active=:active WHERE id=:id")
                  .setParameter("code", code).setParameter("name", name).setParameter("emp", employeeId)
                  .setParameter("comm", commissionPct).setParameter("phone", phone).setParameter("email", email)
                  .setParameter("active", isActive).setParameter("id", id).executeUpdate();
                ra.addFlashAttribute("success", "Salesperson berhasil diupdate.");
            }
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal: " + e.getMessage());
        }
        return "redirect:/ui/master/salespersons";
    }

    @Transactional
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            em.createNativeQuery("DELETE FROM md.salesperson WHERE id = :id").setParameter("id", id).executeUpdate();
            ra.addFlashAttribute("success", "Salesperson berhasil dihapus.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal hapus: " + e.getMessage());
        }
        return "redirect:/ui/master/salespersons";
    }
}