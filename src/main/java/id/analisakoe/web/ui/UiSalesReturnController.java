package id.analisakoe.web.ui;
import id.analisakoe.web.ui.SessionInterceptor;
import id.analisakoe.web.ui.SessionUser;

import id.analisakoe.core.menu.MenuService;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/ui/sales/returns")
public class UiSalesReturnController {
    private final MenuService menuService;
    private final EntityManager em;

    public UiSalesReturnController(MenuService menuService, EntityManager em) {
        this.menuService = menuService;
        this.em = em;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public String list(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Sales Returns");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        String sql = "SELECT h.id, h.doc_no, h.doc_date, COALESCE(c.name, '-') as customer_name, " +
                     "COALESCE(h.total_amount, 0) as total_amount, h.status, COALESCE(h.remarks, '-') as remarks " +
                     "FROM sal.sales_return_hdr h LEFT JOIN md.customer c ON h.customer_id = c.id " +
                     "WHERE h.tenant_id = 1 ORDER BY h.id DESC";

        List<Map<String, Object>> returnList = new ArrayList<>();
        try {
            List<Object[]> results = em.createNativeQuery(sql).getResultList();
            for (Object[] row : results) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", row[0]);
                map.put("docNo", row[1] != null ? row[1].toString() : "-");
                Object dateObj = row[2];
                map.put("docDate", dateObj != null ? ((java.sql.Date) dateObj).toLocalDate() : null);
                map.put("customerName", row[3]);
                map.put("totalAmount", row[4]);
                map.put("status", row[5]);
                map.put("remarks", row[6]);
                returnList.add(map);
            }
        } catch (Exception e) { model.addAttribute("error", "Gagal memuat data: " + e.getMessage()); }

        model.addAttribute("returns", returnList);
        return "ui/sales/return_list";
    }

    @GetMapping("/new")
    public String newForm(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "New Sales Return");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        return "ui/sales/return_form";
    }

    @PostMapping("/{id}/confirm")
    @Transactional
    public String confirm(@PathVariable Long id, RedirectAttributes ra) {
        try {
            em.createNativeQuery("UPDATE sal.sales_return_hdr SET status = 'CONFIRMED' WHERE id = :id")
              .setParameter("id", id).executeUpdate();
            ra.addFlashAttribute("success", "Sales Return berhasil di-confirm.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/ui/sales/returns";
    }

    @PostMapping("/{id}/unconfirm")
    @Transactional
    public String unconfirm(@PathVariable Long id, RedirectAttributes ra) {
        try {
            em.createNativeQuery("UPDATE sal.sales_return_hdr SET status = 'DRAFT' WHERE id = :id")
              .setParameter("id", id).executeUpdate();
            ra.addFlashAttribute("success", "Sales Return berhasil di-unconfirm.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/ui/sales/returns";
    }

    @PostMapping("/{id}/void")
    @Transactional
    public String voidSR(@PathVariable Long id, RedirectAttributes ra) {
        try {
            em.createNativeQuery("UPDATE sal.sales_return_hdr SET status = 'VOID' WHERE id = :id")
              .setParameter("id", id).executeUpdate();
            ra.addFlashAttribute("success", "Sales Return berhasil di-void.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/ui/sales/returns";
    }

    @PostMapping("/{id}/delete")
    @Transactional
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            em.createNativeQuery("DELETE FROM sal.sales_return_hdr WHERE id = :id AND status = 'DRAFT'")
              .setParameter("id", id).executeUpdate();
            ra.addFlashAttribute("success", "Sales Return berhasil dihapus.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/ui/sales/returns";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, RedirectAttributes ra) {
        ra.addFlashAttribute("error", "Fitur Edit belum tersedia.");
        return "redirect:/ui/sales/returns";
    }
}