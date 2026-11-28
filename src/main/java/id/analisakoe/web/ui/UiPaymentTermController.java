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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/ui/master/payment-terms")
public class UiPaymentTermController {

    @Autowired
    private ContextService context;

    @PersistenceContext
    private EntityManager em;

    private final MenuService menuService;

    public UiPaymentTermController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping
    public String list(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Payment Terms");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        List<Object[]> items = em.createNativeQuery("SELECT id, code, name, days, COALESCE(description,'-'), is_active FROM md.payment_term "
                + "WHERE tenant_id = :tenantId ORDER BY days")
                .setParameter("tenantId", context.tenantId()).getResultList();
        model.addAttribute("items", items);
        return "ui/master/payment_term_list";
    }

    @GetMapping("/new")
    public String newForm(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "New Payment Term");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("isNew", true);
        return "ui/master/payment_term_form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Edit Payment Term");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("isNew", false);
        try {
            Object[] row = (Object[]) em.createNativeQuery("SELECT id, code, name, days, COALESCE(description,''), is_active FROM md.payment_term "
                + "WHERE id = :id AND tenant_id = :tenantId")
                .setParameter("id", id).setParameter("tenantId", context.tenantId()).getSingleResult();
            Map<String, Object> c = new HashMap<>();
            c.put("id", row[0]); c.put("code", row[1]); c.put("name", row[2]); c.put("days", row[3]);
            c.put("description", row[4]); c.put("isActive", row[5]);
            model.addAttribute("c", c);
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Data tidak ditemukan.");
            return "redirect:/ui/master/payment-terms";
        }
        return "ui/master/payment_term_form";
    }

    @Transactional
    @PostMapping("/save")
    public String save(@RequestParam(required = false) Long id,
                       @RequestParam String code, @RequestParam String name,
                       @RequestParam Integer days,
                       @RequestParam(required = false) String description,
                       @RequestParam(defaultValue = "true") Boolean isActive,
                       RedirectAttributes ra) {
        try {
            if (id == null) {
                em.createNativeQuery("INSERT INTO md.payment_term (tenant_id, code, name, days, description, is_active) VALUES (:tenantId, :code, :name, :days, :desc, :active)")
                  .setParameter("code", code).setParameter("name", name).setParameter("days", days)
                  .setParameter("desc", description).setParameter("active", isActive).setParameter("tenantId", context.tenantId()).executeUpdate();
                ra.addFlashAttribute("success", "Payment Term berhasil ditambahkan.");
            } else {
                em.createNativeQuery("UPDATE md.payment_term SET code=:code, name=:name, days=:days, description=:desc, "
                  + "is_active=:active WHERE id=:id AND tenant_id=:tenantId")
                  .setParameter("code", code).setParameter("name", name).setParameter("days", days)
                  .setParameter("desc", description).setParameter("active", isActive)
                  .setParameter("id", id).setParameter("tenantId", context.tenantId()).executeUpdate();
                ra.addFlashAttribute("success", "Payment Term berhasil diupdate.");
            }
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal: " + e.getMessage());
        }
        return "redirect:/ui/master/payment-terms";
    }

    @Transactional
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            em.createNativeQuery("DELETE FROM md.payment_term WHERE id = :id AND tenant_id = :tenantId")
                .setParameter("id", id).setParameter("tenantId", context.tenantId()).executeUpdate();
            ra.addFlashAttribute("success", "Payment Term berhasil dihapus.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal hapus: " + e.getMessage());
        }
        return "redirect:/ui/master/payment-terms";
    }
}