package id.analisakoe.web.ui;

import org.springframework.beans.factory.annotation.Autowired;

import id.analisakoe.context.ContextService;

import id.analisakoe.core.menu.MenuService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.access.AccessDeniedException;
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

    @Autowired
    private ContextService context;

    @PersistenceContext
    private EntityManager em;

    private final MenuService menuService;

    public UiSalespersonController(MenuService menuService) {
        this.menuService = menuService;
    }

    private void commonModel(HttpSession session, Model model, String title) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", title);
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
    }

    /** Company yang boleh dipilih user yang sedang login (sesuai user_company). */
    private List<Object[]> allowedCompanies(HttpSession session) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        return em.createNativeQuery(
                "SELECT c.id, c.code, c.name FROM core.company c " +
                "WHERE c.tenant_id = :tenantId AND c.status = 'ACTIVE' " +
                "AND c.id IN (SELECT uc.company_id FROM core.user_company uc WHERE uc.user_id = :userId) " +
                "ORDER BY c.name")
            .setParameter("tenantId", context.tenantId())
            .setParameter("userId", user.getId())
            .getResultList();
    }

    private void validateCompany(HttpSession session, Long companyId) {
        if (companyId == null) {
            throw new IllegalArgumentException("Company wajib dipilih.");
        }
        boolean allowed = allowedCompanies(session).stream()
            .anyMatch(row -> ((Number) row[0]).longValue() == companyId.longValue());
        if (!allowed) {
            throw new AccessDeniedException("Company tidak tersedia untuk user ini.");
        }
    }

    @GetMapping
    public String list(HttpSession session, Model model) {
        commonModel(session, model, "Salespersons");
        List<Object[]> items = em.createNativeQuery(
                "SELECT s.id, s.code, s.name, COALESCE(c.name, '-'), " +
                "COALESCE(s.phone,'-'), COALESCE(s.email,'-'), " +
                "COALESCE(s.commission_pct,0), s.is_active " +
                "FROM md.salesperson s LEFT JOIN core.company c " +
                "ON c.id = s.company_id AND c.tenant_id = s.tenant_id " +
                "WHERE s.tenant_id = :tenantId AND s.company_id = :compId ORDER BY s.code")
            .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
            .getResultList();
        model.addAttribute("items", items);
        return "ui/master/salesperson_list";
    }

    @GetMapping("/new")
    public String newForm(HttpSession session, Model model) {
        commonModel(session, model, "New Salesperson");
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("isNew", true);
        model.addAttribute("companies", allowedCompanies(session));
        model.addAttribute("employees", em.createNativeQuery(
                "SELECT id, nip, name, company_id FROM hr.employee " +
                "WHERE status = 'ACTIVE' AND tenant_id = :tenantId " +
                "AND company_id IN (SELECT company_id FROM core.user_company WHERE user_id = :userId) " +
                "ORDER BY name")
            .setParameter("tenantId", context.tenantId())
            .setParameter("userId", user.getId()).getResultList());
        return "ui/master/salesperson_form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes ra) {
        commonModel(session, model, "Edit Salesperson");
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("isNew", false);
        model.addAttribute("companies", allowedCompanies(session));
        model.addAttribute("employees", em.createNativeQuery(
                "SELECT id, nip, name, company_id FROM hr.employee " +
                "WHERE status = 'ACTIVE' AND tenant_id = :tenantId " +
                "AND company_id IN (SELECT company_id FROM core.user_company WHERE user_id = :userId) " +
                "ORDER BY name")
            .setParameter("tenantId", context.tenantId())
            .setParameter("userId", user.getId()).getResultList());
        try {
            Object[] row = (Object[]) em.createNativeQuery(
                    "SELECT id, code, name, employee_id, COALESCE(commission_pct,0), " +
                    "COALESCE(phone,''), COALESCE(email,''), is_active, company_id " +
                    "FROM md.salesperson WHERE id = :id AND tenant_id = :tenantId AND company_id = :compId")
                .setParameter("id", id)
                .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                .getSingleResult();
            Map<String, Object> c = new HashMap<>();
            c.put("id", row[0]); c.put("code", row[1]); c.put("name", row[2]); c.put("employeeId", row[3]);
            c.put("commissionPct", row[4]); c.put("phone", row[5]); c.put("email", row[6]); c.put("isActive", row[7]);
            c.put("companyId", row[8]);
            model.addAttribute("c", c);
        } catch (jakarta.persistence.NoResultException e) {
            throw new AccessDeniedException("Data salesperson tidak ditemukan atau di luar company aktif.");
        }
        return "ui/master/salesperson_form";
    }

    @Transactional
    @PostMapping("/save")
    public String save(@RequestParam(required = false) Long id,
                       @RequestParam(required = false) Long companyId,
                       @RequestParam String code, @RequestParam String name,
                       @RequestParam(required = false) Long employeeId,
                       @RequestParam(required = false) BigDecimal commissionPct,
                       @RequestParam(required = false) String phone,
                       @RequestParam(required = false) String email,
                       @RequestParam(defaultValue = "true") Boolean isActive,
                       HttpSession session,
                       RedirectAttributes ra) {
        final Long tenantId = context.tenantId();
        final Long targetCompany = companyId != null ? companyId : context.companyId();
        validateCompany(session, targetCompany);
        try {
            if (employeeId != null) {
                boolean employeeValid = !em.createNativeQuery(
                        "SELECT id FROM hr.employee WHERE id = :id AND tenant_id = :tenantId " +
                        "AND company_id = :compId AND status = 'ACTIVE'")
                    .setParameter("id", employeeId)
                    .setParameter("tenantId", tenantId).setParameter("compId", targetCompany)
                    .getResultList().isEmpty();
                if (!employeeValid) {
                    throw new IllegalArgumentException("Employee tidak valid untuk company terpilih.");
                }
            }
            if (id == null) {
                em.createNativeQuery("INSERT INTO md.salesperson " +
                        "(tenant_id, company_id, code, name, employee_id, commission_pct, phone, email, is_active) " +
                        "VALUES (:tenantId, :compId, :code, :name, :emp, :comm, :phone, :email, :active)")
                    .setParameter("tenantId", tenantId).setParameter("compId", targetCompany)
                    .setParameter("code", code).setParameter("name", name).setParameter("emp", employeeId)
                    .setParameter("comm", commissionPct).setParameter("phone", phone).setParameter("email", email)
                    .setParameter("active", isActive).executeUpdate();
                ra.addFlashAttribute("success", "Salesperson berhasil ditambahkan.");
            } else {
                int updated = em.createNativeQuery("UPDATE md.salesperson SET company_id = :compId, " +
                        "code = :code, name = :name, employee_id = :emp, commission_pct = :comm, " +
                        "phone = :phone, email = :email, is_active = :active " +
                        "WHERE id = :id AND tenant_id = :tenantId AND company_id = :oldCompId")
                    .setParameter("compId", targetCompany)
                    .setParameter("code", code).setParameter("name", name).setParameter("emp", employeeId)
                    .setParameter("comm", commissionPct).setParameter("phone", phone).setParameter("email", email)
                    .setParameter("active", isActive).setParameter("id", id)
                    .setParameter("tenantId", tenantId).setParameter("oldCompId", context.companyId())
                    .executeUpdate();
                if (updated == 0) {
                    throw new AccessDeniedException("Data salesperson tidak ditemukan atau di luar company aktif.");
                }
                ra.addFlashAttribute("success", "Salesperson berhasil diupdate.");
            }
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal: " + e.getMessage());
        }
        return "redirect:/ui/master/salespersons";
    }

    @Transactional
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            boolean owned = !em.createNativeQuery(
                    "SELECT id FROM md.salesperson WHERE id = :id AND tenant_id = :tenantId AND company_id = :compId")
                .setParameter("id", id)
                .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                .getResultList().isEmpty();
            if (!owned) {
                throw new AccessDeniedException("Data salesperson tidak ditemukan atau di luar company aktif.");
            }
            em.createNativeQuery("DELETE FROM md.salesperson WHERE id = :id AND tenant_id = :tenantId AND company_id = :compId")
                .setParameter("id", id)
                .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                .executeUpdate();
            ra.addFlashAttribute("success", "Salesperson berhasil dihapus.");
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal hapus: " + e.getMessage());
        }
        return "redirect:/ui/master/salespersons";
    }
}
