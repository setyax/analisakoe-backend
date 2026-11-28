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
@RequestMapping("/ui/master/payment-methods")
public class UiPaymentMethodController {

    @Autowired
    private ContextService context;

    @PersistenceContext
    private EntityManager em;

    private final MenuService menuService;

    public UiPaymentMethodController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping
    public String list(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Payment Methods");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        List<Object[]> items = em.createNativeQuery("SELECT id, code, name, is_active FROM fin.payment_method ORDER BY code").getResultList();
        model.addAttribute("items", items);
        return "ui/master/payment_method_list";
    }

    @GetMapping("/new")
    public String newForm(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "New Payment Method");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("isNew", true);
        return "ui/master/payment_method_form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Edit Payment Method");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("isNew", false);
        try {
            Object[] row = (Object[]) em.createNativeQuery("SELECT id, code, name, is_active FROM fin.payment_method WHERE id = :id")
                .setParameter("id", id).getSingleResult();
            Map<String, Object> c = new HashMap<>();
            c.put("id", row[0]); c.put("code", row[1]); c.put("name", row[2]); c.put("isActive", row[3]);
            model.addAttribute("c", c);
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Data tidak ditemukan.");
            return "redirect:/ui/master/payment-methods";
        }
        return "ui/master/payment_method_form";
    }

    @Transactional
    @PostMapping("/save")
    public String save(@RequestParam(required = false) Long id,
                       @RequestParam String code, @RequestParam String name,
                       @RequestParam(defaultValue = "true") Boolean isActive,
                       RedirectAttributes ra) {
        try {
            if (id == null) {
                em.createNativeQuery("INSERT INTO fin.payment_method (tenant_id, code, name, is_active) VALUES (:tenantId, :code, :name, :active)")
                  .setParameter("code", code).setParameter("name", name).setParameter("active", isActive).setParameter("tenantId", context.tenantId()).executeUpdate();
                ra.addFlashAttribute("success", "Payment Method berhasil ditambahkan.");
            } else {
                em.createNativeQuery("UPDATE fin.payment_method SET code=:code, name=:name, is_active=:active WHERE id=:id")
                  .setParameter("code", code).setParameter("name", name).setParameter("active", isActive).setParameter("id", id).executeUpdate();
                ra.addFlashAttribute("success", "Payment Method berhasil diupdate.");
            }
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal: " + e.getMessage());
        }
        return "redirect:/ui/master/payment-methods";
    }

    @Transactional
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            em.createNativeQuery("DELETE FROM fin.payment_method WHERE id = :id").setParameter("id", id).executeUpdate();
            ra.addFlashAttribute("success", "Payment Method berhasil dihapus.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal hapus: " + e.getMessage());
        }
        return "redirect:/ui/master/payment-methods";
    }
}