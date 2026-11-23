package id.analisakoe.web.ui;

import id.analisakoe.core.menu.MenuService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
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
 * User Management.
 * Assignment company memakai core.user_company (tabel yang DIPAKAI LOGIN),
 * bukan sys.user_company. Field default_company_id menentukan company yang
 * aktif saat user pertama kali login.
 */
@Controller
@RequestMapping("/ui/users")
public class UiUserController {

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final PasswordEncoder passwordEncoder;
    private final MenuService menuService;

    public UiUserController(PasswordEncoder passwordEncoder, MenuService menuService) {
        this.passwordEncoder = passwordEncoder;
        this.menuService = menuService;
    }

    // ==================== LIST USER ====================
    @GetMapping
    public String list(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "User Management");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        List<Object[]> users = em.createNativeQuery(
                "SELECT u.id, u.username, u.full_name, u.status, u.email, " +
                "       dc.name " +
                "FROM core.app_user u " +
                "LEFT JOIN core.company dc ON dc.id = u.default_company_id " +
                "ORDER BY u.id").getResultList();
        model.addAttribute("users", users);

        return "ui/users/user_list";
    }

    // ==================== FORM TAMBAH / EDIT USER ====================
    @GetMapping("/new")
    public String newForm(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "New User");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        model.addAttribute("isNew", true);
        prepareFormData(model, null);
        return "ui/users/user_form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Edit User");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        model.addAttribute("isNew", false);
        prepareFormData(model, id);
        return "ui/users/user_form";
    }

    // Helper untuk memuat data ke Form
    private void prepareFormData(Model model, Long userId) {
        // Company dari core.company (tabel yang dipakai login & modul)
        List<Object[]> companies = em.createNativeQuery(
                "SELECT id, name, status FROM core.company ORDER BY name").getResultList();
        model.addAttribute("allCompanies", companies);

        List<String> allPermissions = List.of("DASHBOARD", "MASTER_DATA", "PURCHASING", "SALES", "INVENTORY", "FINANCE", "HRGA", "USER_MGMT");
        model.addAttribute("allPermissions", allPermissions);

        List<Long> userCompanies = new ArrayList<>();
        List<String> userPerms = new ArrayList<>();
        Long defaultCompanyId = null;
        Map<String, Object> u = null;

        if (userId != null) {
            try {
                Object[] row = (Object[]) em.createNativeQuery(
                        "SELECT id, username, email, full_name, status, default_company_id FROM core.app_user WHERE id = :id")
                        .setParameter("id", userId).getSingleResult();

                u = new HashMap<>();
                u.put("id", row[0]);
                u.put("username", row[1] != null ? row[1] : "");
                u.put("email", row[2] != null ? row[2] : "");
                u.put("fullName", row[3] != null ? row[3] : "");
                u.put("status", row[4] != null ? row[4] : "ACTIVE");
                defaultCompanyId = row[5] != null ? ((Number) row[5]).longValue() : null;

                List<?> uc = em.createNativeQuery("SELECT company_id FROM core.user_company WHERE user_id = :id")
                        .setParameter("id", userId).getResultList();
                for (Object o : uc) userCompanies.add(((Number) o).longValue());

                List<?> up = em.createNativeQuery("SELECT permission_code FROM sys.user_permission WHERE user_id = :id")
                        .setParameter("id", userId).getResultList();
                for (Object o : up) userPerms.add((String) o);

            } catch (Exception e) {
                System.out.println("Error loading user: " + e.getMessage());
            }
        }

        model.addAttribute("u", u);
        model.addAttribute("userCompanies", userCompanies);
        model.addAttribute("userPerms", userPerms);
        model.addAttribute("defaultCompanyId", defaultCompanyId);
    }

    // ==================== PROSES SIMPAN USER ====================
    @PostMapping("/save")
    public String save(@RequestParam(required = false) Long id,
                       @RequestParam String username,
                       @RequestParam(required = false) String email,
                       @RequestParam(required = false) String password,
                       @RequestParam String fullName,
                       @RequestParam String status,
                       @RequestParam(required = false) List<Long> companyIds,
                       @RequestParam(required = false) Long defaultCompanyId,
                       @RequestParam(required = false) List<String> permissions,
                       RedirectAttributes ra) {
        try {
            transactionTemplate.executeWithoutResult(tx -> {
                final Long userId;
                if (id == null) {
                    // --- CREATE USER BARU ---
                    if (password == null || password.trim().isEmpty()) {
                        throw new IllegalArgumentException("Password wajib diisi untuk user baru.");
                    }
                    String hashedPassword = passwordEncoder.encode(password);

                    Object newId = em.createNativeQuery("INSERT INTO core.app_user (tenant_id, username, email, password_hash, full_name, status, must_change_pwd, default_company_id) " +
                                         "VALUES (1, :u, :e, :p, :f, :s, false, :d) RETURNING id")
                      .setParameter("u", username)
                      .setParameter("e", email)
                      .setParameter("p", hashedPassword)
                      .setParameter("f", fullName)
                      .setParameter("s", status)
                      .setParameter("d", defaultCompanyId)
                      .getSingleResult();
                    userId = ((Number) newId).longValue();
                } else {
                    // --- UPDATE USER ---
                    userId = id;
                    if (password != null && !password.trim().isEmpty()) {
                        String hashedPassword = passwordEncoder.encode(password);
                        em.createNativeQuery("UPDATE core.app_user SET username = :u, email = :e, password_hash = :p, full_name = :f, status = :s, default_company_id = :d WHERE id = :id")
                          .setParameter("u", username)
                          .setParameter("e", email)
                          .setParameter("p", hashedPassword)
                          .setParameter("f", fullName)
                          .setParameter("s", status)
                          .setParameter("d", defaultCompanyId)
                          .setParameter("id", id)
                          .executeUpdate();
                    } else {
                        em.createNativeQuery("UPDATE core.app_user SET username = :u, email = :e, full_name = :f, status = :s, default_company_id = :d WHERE id = :id")
                          .setParameter("u", username)
                          .setParameter("e", email)
                          .setParameter("f", fullName)
                          .setParameter("s", status)
                          .setParameter("d", defaultCompanyId)
                          .setParameter("id", id)
                          .executeUpdate();
                    }
                }

                // Validasi: default company harus termasuk salah satu company yang di-assign.
                // Jika kosong/tidak valid, pakai company pertama yang di-assign.
                final List<Long> assigned = (companyIds != null) ? companyIds : new ArrayList<>();
                Long defId = defaultCompanyId;
                if (defId == null || !assigned.contains(defId)) {
                    defId = assigned.isEmpty() ? null : assigned.get(0);
                }

                // SIMPAN MAPPING COMPANY (core.user_company — dipakai login)
                em.createNativeQuery("DELETE FROM core.user_company WHERE user_id = :id").setParameter("id", userId).executeUpdate();
                for (Long cid : assigned) {
                    em.createNativeQuery("INSERT INTO core.user_company (user_id, company_id) VALUES (:id, :cid) ON CONFLICT DO NOTHING")
                      .setParameter("id", userId).setParameter("cid", cid).executeUpdate();
                }

                if (defId != null) {
                    em.createNativeQuery("UPDATE core.app_user SET default_company_id = :d WHERE id = :id")
                      .setParameter("d", defId).setParameter("id", userId).executeUpdate();
                }

                // SIMPAN MAPPING PERMISSION
                em.createNativeQuery("DELETE FROM sys.user_permission WHERE user_id = :id").setParameter("id", userId).executeUpdate();
                if (permissions != null) {
                    for (String p : permissions) {
                        em.createNativeQuery("INSERT INTO sys.user_permission (user_id, permission_code) VALUES (:id, :p)")
                          .setParameter("id", userId).setParameter("p", p).executeUpdate();
                    }
                }
            });

            ra.addFlashAttribute("success", "Data User \"" + username + "\" berhasil disimpan.");
        } catch (Exception e) {
            String msg = rootMessage(e);
            if (msg != null && msg.toLowerCase().contains("duplicate key")) {
                msg = "Username \"" + username + "\" sudah dipakai user lain.";
            }
            ra.addFlashAttribute("error", "Gagal menyimpan: " + msg);
        }
        return "redirect:/ui/users";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        SessionUser current = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        try {
            transactionTemplate.executeWithoutResult(tx -> {
                if (current != null && id.equals(current.getId()))
                    throw new IllegalStateException("Tidak bisa menghapus akun sendiri.");

                List<?> rows = em.createNativeQuery("SELECT username FROM core.app_user WHERE id = :id")
                        .setParameter("id", id).getResultList();
                if (rows.isEmpty()) throw new IllegalStateException("User tidak ditemukan.");
                String username = rows.get(0).toString();

                // Hak akses permission (sys.user_permission tidak ter-cascade FK)
                em.createNativeQuery("DELETE FROM sys.user_permission WHERE user_id = :id")
                    .setParameter("id", id).executeUpdate();
                // user_role, user_company, refresh_token ter-cascade oleh database
                em.createNativeQuery("DELETE FROM core.app_user WHERE id = :id")
                    .setParameter("id", id).executeUpdate();

                ra.addFlashAttribute("success", "User \"" + username + "\" berhasil dihapus.");
            });
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal hapus user: " + rootMessage(e));
        }
        return "redirect:/ui/users";
    }

    private String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
        return cur.getMessage() != null ? cur.getMessage() : cur.toString();
    }
}
