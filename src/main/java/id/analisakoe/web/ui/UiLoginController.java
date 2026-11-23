package id.analisakoe.web.ui;

import id.analisakoe.auth.dto.LoginRequest;
import id.analisakoe.auth.service.AuthService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/ui")
public class UiLoginController {

    private final AuthService authService;

    @PersistenceContext
    private EntityManager em; // <-- Tambahan untuk query database

    public UiLoginController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping({"", "/"})
    public String root(HttpSession session) {
        if (session.getAttribute(SessionInterceptor.SESSION_USER) == null) {
            return "redirect:/ui/login";
        }
        return "redirect:/ui/dashboard";
    }

    @GetMapping("/login")
    public String loginPage(HttpSession session, @RequestParam(required = false) String redirect, Model model) {
        if (session.getAttribute(SessionInterceptor.SESSION_USER) != null) {
            return "redirect:/ui/dashboard";
        }
        model.addAttribute("redirect", redirect);
        return "ui/login";
    }

    @PostMapping("/login")
    public String doLogin(@RequestParam String username,
                          @RequestParam String password,
                          @RequestParam(required = false) String redirect,
                          HttpServletRequest request,
                          Model model) {
        try {
            var resp = authService.login(
                new LoginRequest(username, password),
                request.getHeader("User-Agent"),
                request.getRemoteAddr()
            );
            Long tenantId = resp.user().tenantId();

            if (tenantId == null) {
                throw new IllegalStateException(
                    "Tenant pengguna tidak ditemukan. Hubungi Administrator."
                );
            }

            // 1. Ambil Permission dari Database
List<String> userPerms = new ArrayList<>();
List<?> perms = em.createNativeQuery("SELECT permission_code FROM sys.user_permission WHERE user_id = :id")
                  .setParameter("id", resp.user().id()).getResultList();
for (Object p : perms) userPerms.add((String) p);

// 2. Buat SessionUser dengan permission dari database
SessionUser user = new SessionUser(
    resp.user().id(),
    resp.user().username(),
    resp.user().fullName(),
    resp.accessToken(),
    resp.refreshToken(),
    resp.user().roles(),
    new java.util.HashSet<>(userPerms) // <-- Gunakan permission dari DB
);
            user.setTenantId(tenantId);

            // 2. AMBIL DAFTAR CABANG DARI DATABASE BERDASARKAN USER ID
                        String sql =
                "SELECT c.id, c.name " +
                "FROM core.company c " +
                "JOIN core.user_company uc ON uc.company_id = c.id " +
                "JOIN core.app_user au ON au.id = uc.user_id " +
                "WHERE uc.user_id = :userId " +
                "AND au.tenant_id = :tenantId " +
                "AND c.tenant_id = :tenantId " +
                "AND c.status = 'ACTIVE' " +
                "ORDER BY c.name";
            
            List<Object[]> rows = em.createNativeQuery(sql)
                                    .setParameter("userId", resp.user().id())
                                    .setParameter("tenantId", tenantId)
                                    .getResultList();

            List<Map<String, Object>> allowedCompanies = new ArrayList<>();
            Long defaultCompanyId = null;
            String defaultCompanyName = null;

            // Company default yang dipilih admin di User Management (jika ada & masih aktif)
            Long preferredCompanyId = null;
            try {
                Object d = em.createNativeQuery("SELECT default_company_id FROM core.app_user WHERE id = :id")
                        .setParameter("id", resp.user().id()).getSingleResult();
                if (d != null) preferredCompanyId = ((Number) d).longValue();
            } catch (Exception ignore) { }

            for (Object[] row : rows) {
                Long cId = ((Number) row[0]).longValue();
                String cName = (String) row[1];
                allowedCompanies.add(Map.of("id", cId, "name", cName));
                
                // Prioritas 1: default_company_id dari User Management.
                // Prioritas 2: cabang pertama (urut nama) sebagai fallback.
                if (preferredCompanyId != null && preferredCompanyId.equals(cId)) {
                    defaultCompanyId = cId;
                    defaultCompanyName = cName;
                }
                if (defaultCompanyId == null) {
                    defaultCompanyId = cId;
                    defaultCompanyName = cName;
                }
            }

            // Jika user tidak di-assign ke cabang manapun, tolak login
            if (defaultCompanyId == null) {
                throw new Exception("Akun Anda belum diberikan akses ke cabang manapun. Hubungi Admin.");
            }

            // 3. Set data cabang ke SessionUser
            user.setAllowedCompanies(allowedCompanies);
            user.setActiveCompanyId(defaultCompanyId);
            user.setActiveCompanyName(defaultCompanyName);

            // 4. Simpan ke session
            request.getSession(true).setAttribute(SessionInterceptor.SESSION_USER, user);

            // 5. Redirect
            if (redirect != null && !redirect.isBlank() && redirect.startsWith("/")) {
                String safeRedirect = redirect;
                if (safeRedirect.startsWith("/api/")) {
                    safeRedirect = safeRedirect.substring(4); // buang "/api"
                }
                return "redirect:" + safeRedirect;
            }
            return "redirect:/ui/dashboard";
            
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage()); // Tampilkan pesan error asli (misal: belum punya cabang)
            model.addAttribute("username", username);
            model.addAttribute("redirect", redirect);
            return "ui/login";
        }
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        if (user != null) {
            try {
                authService.logout(user.getRefreshToken());
            } catch (Exception ignored) {}
        }
        session.invalidate();
        return "redirect:/ui/login";
    }
}