package id.analisakoe.web.ui;

import id.analisakoe.context.ContextService;

import id.analisakoe.core.menu.MenuService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Master Company.
 * Tabel yang DIPAKAI APLIKASI adalah core.company (login, cabang aktif,
 * modul-modul). Tabel sys.company tidak lagi dipakai di mana pun.
 */
@Controller
@RequestMapping("/ui/master/companies")
public class UiCompanyController {

    @Autowired
    private ContextService context;

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final MenuService menuService;

    public UiCompanyController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping
    public String list(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Master Companies");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        // Kolom ke-6 (index) dipakai company_list.html: id, code, name, address, phone, email, status, jumlahUser
        List<Object[]> companies = em.createNativeQuery(
                "SELECT c.id, c.code, c.name, c.address, c.phone, c.email, c.status, " +
                "       (SELECT COUNT(*) FROM core.user_company uc WHERE uc.company_id = c.id) " +
                "FROM core.company c ORDER BY c.id")
                .getResultList();
        model.addAttribute("companies", companies);
        return "ui/master/company_list";
    }

    @GetMapping("/new")
    public String newForm(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "New Company");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("isNew", true);
        return "ui/master/company_form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Edit Company");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("isNew", false);

        try {
            Object[] row = (Object[]) em.createNativeQuery(
                    "SELECT id, code, name, address, phone, email, status FROM core.company WHERE id = :id")
                    .setParameter("id", id).getSingleResult();

            Map<String, Object> c = new HashMap<>();
            c.put("id", row[0]);
            c.put("code", row[1] != null ? row[1] : "");
            c.put("name", row[2] != null ? row[2] : "");
            c.put("address", row[3] != null ? row[3] : "");
            c.put("phone", row[4] != null ? row[4] : "");
            c.put("email", row[5] != null ? row[5] : "");
            c.put("status", row[6] != null ? row[6] : "ACTIVE");

            model.addAttribute("c", c);
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Company tidak ditemukan.");
            return "redirect:/ui/master/companies";
        }
        return "ui/master/company_form";
    }

    @PostMapping("/save")
    public String save(@RequestParam(required = false) Long id,
                       @RequestParam String code,
                       @RequestParam String name,
                       @RequestParam(required = false) String address,
                       @RequestParam(required = false) String phone,
                       @RequestParam(required = false) String email,
                       @RequestParam(required = false) String status,
                       HttpSession session,
                       RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long tenantId = context.tenantId();
        String statusVal = (status == null || status.isBlank()) ? "ACTIVE" : status;

        try {
            transactionTemplate.executeWithoutResult(tx -> {
                if (id == null) {
                    // INSERT ke core.company (tabel yang dipakai login & modul)
                    Object newIdObj = em.createNativeQuery(
                            "INSERT INTO core.company (tenant_id, code, name, address, phone, email, status) " +
                            "VALUES (:tenantId, :code, :name, :addr, :phone, :email, :status) RETURNING id")
                        .setParameter("tenantId", tenantId)
                        .setParameter("code", code)
                        .setParameter("name", name)
                        .setParameter("addr", address)
                        .setParameter("phone", phone)
                        .setParameter("email", email)
                        .setParameter("status", statusVal)
                        .getSingleResult();
                    Long newId = ((Number) newIdObj).longValue();

                    // Auto-assign company baru ke user yang membuatnya agar
                    // langsung bisa dipilih di dropdown "CABANG AKTIF".
                    if (user != null && user.getId() != null) {
                        em.createNativeQuery(
                                "INSERT INTO core.user_company (user_id, company_id) " +
                                "VALUES (:userId, :companyId) ON CONFLICT DO NOTHING")
                            .setParameter("userId", user.getId())
                            .setParameter("companyId", newId)
                            .executeUpdate();
                    }
                    ra.addFlashAttribute("success", "Company \"" + name + "\" berhasil ditambahkan.");
                } else {
                    em.createNativeQuery(
                            "UPDATE core.company SET code = :code, name = :name, address = :addr, " +
                            "phone = :phone, email = :email, status = :status, updated_at = now() WHERE id = :id")
                      .setParameter("code", code)
                      .setParameter("name", name)
                      .setParameter("addr", address)
                      .setParameter("phone", phone)
                      .setParameter("email", email)
                      .setParameter("status", statusVal)
                      .setParameter("id", id)
                      .executeUpdate();
                    ra.addFlashAttribute("success", "Company \"" + name + "\" berhasil diupdate.");
                }
            });
        } catch (Exception e) {
            // TransactionTemplate melempar exception ASLI (bukan
            // UnexpectedRollbackException), jadi pesannya informatif.
            String msg = rootMessage(e);
            if (msg != null && msg.toLowerCase().contains("duplicate key")) {
                msg = "Kode company \"" + code + "\" sudah dipakai company lain.";
            }
            ra.addFlashAttribute("error", "Gagal menyimpan company: " + msg);
        }
        return "redirect:/ui/master/companies";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            transactionTemplate.executeWithoutResult(tx -> {
                Long usedByUsers = ((Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM core.user_company WHERE company_id = :id")
                        .setParameter("id", id).getSingleResult()).longValue();
                if (usedByUsers > 0) {
                    throw new IllegalStateException(
                        "Tidak bisa dihapus. Company ini di-assign ke " + usedByUsers + " user. " +
                        "Lepaskan assignment user terlebih dahulu.");
                }
                int deleted = em.createNativeQuery("DELETE FROM core.company WHERE id = :id")
                        .setParameter("id", id).executeUpdate();
                if (deleted == 0) {
                    throw new IllegalStateException("Company tidak ditemukan.");
                }
            });
            ra.addFlashAttribute("success", "Company berhasil dihapus.");
        } catch (Exception e) {
            String msg = rootMessage(e);
            if (msg != null && (msg.contains("foreign key constraint") || msg.contains("violates foreign"))) {
                msg = "Tidak bisa dihapus. Company ini masih dipakai data transaksi/master lain.";
            }
            ra.addFlashAttribute("error", "Gagal hapus: " + msg);
        }
        return "redirect:/ui/master/companies";
    }

    private String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
        return cur.getMessage() != null ? cur.getMessage() : cur.toString();
    }
}
