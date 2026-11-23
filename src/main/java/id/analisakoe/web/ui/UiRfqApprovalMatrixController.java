package id.analisakoe.web.ui;

import id.analisakoe.core.menu.MenuService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
@RequestMapping("/ui/purchasing/rfq/approval-matrix")
public class UiRfqApprovalMatrixController {

    @PersistenceContext
    private EntityManager em;

    private final MenuService menuService;

    public UiRfqApprovalMatrixController(MenuService menuService) {
        this.menuService = menuService;
    }

    private SessionUser requireManager(HttpSession session) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session pengguna tidak ditemukan.");
        if (!canManage(user)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Anda tidak memiliki hak mengelola RFQ Approval Matrix.");
        return user;
    }

    private Long tenantId(SessionUser user) {
        return user.getTenantId() != null ? user.getTenantId() : 1L;
    }

    private String normalize(String value) {
        if (value == null) return "";
        String result = value.trim().toUpperCase(java.util.Locale.ROOT);
        return result.startsWith("ROLE_") ? result.substring(5) : result;
    }

    private boolean canManage(SessionUser user) {
        Set<String> roles = user.getRoles();
        if (roles != null) {
            for (String role : roles) {
                String value = normalize(role);
                if ("ADMIN".equals(value) || "SUPER_ADMIN".equals(value) || "SYSADMIN".equals(value)) return true;
            }
        }
        Set<String> permissions = user.getPermissions();
        if (permissions != null) {
            for (String permission : permissions) {
                String value = permission == null ? "" : permission.trim().toLowerCase(java.util.Locale.ROOT);
                if ("*".equals(value) || "admin.all".equals(value) ||
                        "pur.rfq.approval_matrix.manage".equals(value) ||
                        "rfq.approval_matrix.manage".equals(value) ||
                        "purchasing.approval.manage".equals(value)) return true;
            }
        }
        return false;
    }

    private void addPageModel(SessionUser user, Model model, String title) {
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", title);
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
    }

    @GetMapping
    public String list(HttpSession session, Model model) {
        SessionUser user = requireManager(session);
        addPageModel(user, model, "RFQ Approval Matrix");
        List<Map<String, Object>> rows = new ArrayList<>();
        List<Object[]> result = em.createNativeQuery(
                "SELECT id, level, min_amount, max_amount, role_required, is_active, created_at " +
                "FROM core.approval_matrix WHERE tenant_id = :tenant AND doc_type = 'RFQ' " +
                "ORDER BY min_amount, level")
                .setParameter("tenant", tenantId(user)).getResultList();
        for (Object[] row : result) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", row[0]);
            item.put("level", row[1]);
            item.put("minAmount", row[2]);
            item.put("maxAmount", row[3]);
            item.put("roleRequired", row[4]);
            item.put("isActive", row[5]);
            item.put("createdAt", row[6]);
            rows.add(item);
        }
        model.addAttribute("matrices", rows);
        return "ui/purchasing/rfq_approval_matrix_list";
    }

    @GetMapping("/new")
    public String newForm(HttpSession session, Model model) {
        SessionUser user = requireManager(session);
        addPageModel(user, model, "Tambah RFQ Approval Matrix");
        Map<String, Object> matrix = new HashMap<>();
        matrix.put("level", 1);
        matrix.put("minAmount", BigDecimal.ZERO);
        matrix.put("maxAmount", new BigDecimal("999999999999"));
        matrix.put("roleRequired", "MANAGER");
        matrix.put("isActive", true);
        model.addAttribute("matrix", matrix);
        model.addAttribute("isNew", true);
        return "ui/purchasing/rfq_approval_matrix_form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes ra) {
        SessionUser user = requireManager(session);
        addPageModel(user, model, "Ubah RFQ Approval Matrix");
        List<Object[]> result = em.createNativeQuery(
                "SELECT id, level, min_amount, max_amount, role_required, is_active " +
                "FROM core.approval_matrix WHERE id = :id AND tenant_id = :tenant AND doc_type = 'RFQ'")
                .setParameter("id", id).setParameter("tenant", tenantId(user)).getResultList();
        if (result.isEmpty()) {
            ra.addFlashAttribute("error", "Data matrix tidak ditemukan.");
            return "redirect:/ui/purchasing/rfq/approval-matrix";
        }
        Object[] row = result.get(0);
        Map<String, Object> matrix = new HashMap<>();
        matrix.put("id", row[0]);
        matrix.put("level", row[1]);
        matrix.put("minAmount", row[2]);
        matrix.put("maxAmount", row[3]);
        matrix.put("roleRequired", row[4]);
        matrix.put("isActive", row[5]);
        model.addAttribute("matrix", matrix);
        model.addAttribute("isNew", false);
        return "ui/purchasing/rfq_approval_matrix_form";
    }

    @PostMapping("/save")
    @Transactional
    public String save(@RequestParam(required = false) Long id,
                       @RequestParam Integer level,
                       @RequestParam BigDecimal minAmount,
                       @RequestParam BigDecimal maxAmount,
                       @RequestParam String roleRequired,
                       @RequestParam(defaultValue = "false") boolean isActive,
                       HttpSession session,
                       RedirectAttributes ra) {
        SessionUser user = requireManager(session);
        Long tenant = tenantId(user);
        String role = roleRequired == null ? "" : roleRequired.trim().toUpperCase(java.util.Locale.ROOT);

        if (level == null || level < 1) {
            ra.addFlashAttribute("error", "Level harus berupa angka minimal 1.");
            return redirectForm(id);
        }
        if (minAmount == null || maxAmount == null || minAmount.compareTo(BigDecimal.ZERO) < 0 || maxAmount.compareTo(minAmount) <= 0) {
            ra.addFlashAttribute("error", "Rentang nominal tidak valid. Nilai maksimal harus lebih besar dari nilai minimal.");
            return redirectForm(id);
        }
        if (role.isEmpty() || role.length() > 100 || !role.matches("[A-Z0-9_.-]+")) {
            ra.addFlashAttribute("error", "Role approver wajib diisi. Gunakan huruf, angka, titik, garis bawah, atau tanda hubung.");
            return redirectForm(id);
        }

        Long duplicateLevel;
        if (id == null) {
            duplicateLevel = ((Number) em.createNativeQuery(
                    "SELECT COUNT(*) FROM core.approval_matrix " +
                    "WHERE tenant_id = :tenant AND doc_type = 'RFQ' AND level = :level")
                    .setParameter("tenant", tenant).setParameter("level", level).getSingleResult()).longValue();
        } else {
            duplicateLevel = ((Number) em.createNativeQuery(
                    "SELECT COUNT(*) FROM core.approval_matrix " +
                    "WHERE tenant_id = :tenant AND doc_type = 'RFQ' AND level = :level AND id <> :id")
                    .setParameter("tenant", tenant).setParameter("level", level)
                    .setParameter("id", id).getSingleResult()).longValue();
        }
        if (duplicateLevel > 0) {
            ra.addFlashAttribute("error", "Level " + level + " sudah digunakan oleh matrix RFQ lain pada tenant ini.");
            return redirectForm(id);
        }

        if (isActive) {
            Long overlaps;
            if (id == null) {
                overlaps = ((Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM core.approval_matrix " +
                        "WHERE tenant_id = :tenant AND doc_type = 'RFQ' AND is_active = true " +
                        "AND :minAmount < max_amount AND :maxAmount > min_amount")
                        .setParameter("tenant", tenant).setParameter("minAmount", minAmount)
                        .setParameter("maxAmount", maxAmount).getSingleResult()).longValue();
            } else {
                overlaps = ((Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM core.approval_matrix " +
                        "WHERE tenant_id = :tenant AND doc_type = 'RFQ' AND is_active = true " +
                        "AND id <> :id AND :minAmount < max_amount AND :maxAmount > min_amount")
                        .setParameter("tenant", tenant).setParameter("id", id)
                        .setParameter("minAmount", minAmount).setParameter("maxAmount", maxAmount)
                        .getSingleResult()).longValue();
            }
            if (overlaps > 0) {
                ra.addFlashAttribute("error", "Rentang nominal tumpang tindih dengan matrix RFQ aktif lain. Gunakan batas atas eksklusif.");
                return redirectForm(id);
            }
        }

        if (id == null) {
            em.createNativeQuery(
                    "INSERT INTO core.approval_matrix " +
                    "(tenant_id, doc_type, level, min_amount, max_amount, role_required, is_active) " +
                    "VALUES (:tenant, 'RFQ', :level, :minAmount, :maxAmount, :role, :active)")
                    .setParameter("tenant", tenant).setParameter("level", level)
                    .setParameter("minAmount", minAmount).setParameter("maxAmount", maxAmount)
                    .setParameter("role", role).setParameter("active", isActive).executeUpdate();
            ra.addFlashAttribute("success", "RFQ Approval Matrix berhasil ditambahkan.");
        } else {
            int updated = em.createNativeQuery(
                    "UPDATE core.approval_matrix SET level = :level, min_amount = :minAmount, " +
                    "max_amount = :maxAmount, role_required = :role, is_active = :active " +
                    "WHERE id = :id AND tenant_id = :tenant AND doc_type = 'RFQ'")
                    .setParameter("level", level).setParameter("minAmount", minAmount)
                    .setParameter("maxAmount", maxAmount).setParameter("role", role)
                    .setParameter("active", isActive).setParameter("id", id)
                    .setParameter("tenant", tenant).executeUpdate();
            if (updated == 0) {
                ra.addFlashAttribute("error", "Data matrix tidak ditemukan atau bukan milik tenant ini.");
            } else {
                ra.addFlashAttribute("success", "RFQ Approval Matrix berhasil diperbarui.");
            }
        }
        return "redirect:/ui/purchasing/rfq/approval-matrix";
    }

    private String redirectForm(Long id) {
        return id == null ? "redirect:/ui/purchasing/rfq/approval-matrix/new" :
                "redirect:/ui/purchasing/rfq/approval-matrix/" + id + "/edit";
    }

    @PostMapping("/{id}/toggle")
    @Transactional
    public String toggle(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        SessionUser user = requireManager(session);
        Long tenant = tenantId(user);
        List<Object[]> rows = em.createNativeQuery(
                "SELECT is_active, min_amount, max_amount, level FROM core.approval_matrix " +
                "WHERE id = :id AND tenant_id = :tenant AND doc_type = 'RFQ'")
                .setParameter("id", id).setParameter("tenant", tenant).getResultList();
        if (rows.isEmpty()) {
            ra.addFlashAttribute("error", "Matrix tidak ditemukan.");
            return "redirect:/ui/purchasing/rfq/approval-matrix";
        }
        Object[] row = rows.get(0);
        boolean activate = !Boolean.TRUE.equals(row[0]);
        BigDecimal min = new BigDecimal(row[1].toString());
        BigDecimal max = new BigDecimal(row[2].toString());
        if (activate) {
            Long overlaps = ((Number) em.createNativeQuery(
                    "SELECT COUNT(*) FROM core.approval_matrix " +
                    "WHERE tenant_id = :tenant AND doc_type = 'RFQ' AND is_active = true " +
                    "AND id <> :id AND :minAmount < max_amount AND :maxAmount > min_amount")
                    .setParameter("tenant", tenant).setParameter("id", id)
                    .setParameter("minAmount", min).setParameter("maxAmount", max).getSingleResult()).longValue();
            if (overlaps > 0) {
                ra.addFlashAttribute("error", "Matrix tidak dapat diaktifkan karena rentang nominal tumpang tindih.");
                return "redirect:/ui/purchasing/rfq/approval-matrix";
            }
        }
        em.createNativeQuery("UPDATE core.approval_matrix SET is_active = :active WHERE id = :id AND tenant_id = :tenant AND doc_type = 'RFQ'")
                .setParameter("active", activate).setParameter("id", id).setParameter("tenant", tenant).executeUpdate();
        ra.addFlashAttribute("success", activate ? "Matrix berhasil diaktifkan." : "Matrix berhasil dinonaktifkan.");
        return "redirect:/ui/purchasing/rfq/approval-matrix";
    }

    @PostMapping("/{id}/delete")
    @Transactional
    public String delete(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        SessionUser user = requireManager(session);
        Long tenant = tenantId(user);
        List<Object[]> rows = em.createNativeQuery(
                "SELECT level, min_amount, max_amount FROM core.approval_matrix " +
                "WHERE id = :id AND tenant_id = :tenant AND doc_type = 'RFQ'")
                .setParameter("id", id).setParameter("tenant", tenant).getResultList();
        if (rows.isEmpty()) {
            ra.addFlashAttribute("error", "Matrix tidak ditemukan.");
            return "redirect:/ui/purchasing/rfq/approval-matrix";
        }
        Object[] row = rows.get(0);
        BigDecimal min = new BigDecimal(row[1].toString());
        BigDecimal max = new BigDecimal(row[2].toString());

        Long logUses = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM core.approval_log " +
                "WHERE tenant_id = :tenant AND doc_type = 'RFQ' " +
                "AND amount IS NOT NULL AND amount >= :minAmount AND amount < :maxAmount")
                .setParameter("tenant", tenant).setParameter("minAmount", min)
                .setParameter("maxAmount", max).getSingleResult()).longValue();
        Long pendingUses = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM pur.rfq_hdr " +
                "WHERE tenant_id = :tenant AND status = 'PENDING_APPROVAL' " +
                "AND recommended_amount IS NOT NULL " +
                "AND recommended_amount >= :minAmount AND recommended_amount < :maxAmount")
                .setParameter("tenant", tenant).setParameter("minAmount", min)
                .setParameter("maxAmount", max).getSingleResult()).longValue();
        if (logUses > 0 || pendingUses > 0) {
            ra.addFlashAttribute("error", "Matrix sudah dipakai histori approval atau RFQ yang masih pending. Nonaktifkan matrix, jangan hapus permanen.");
            return "redirect:/ui/purchasing/rfq/approval-matrix";
        }

        em.createNativeQuery("DELETE FROM core.approval_matrix WHERE id = :id AND tenant_id = :tenant AND doc_type = 'RFQ'")
                .setParameter("id", id).setParameter("tenant", tenant).executeUpdate();
        ra.addFlashAttribute("success", "Matrix berhasil dihapus.");
        return "redirect:/ui/purchasing/rfq/approval-matrix";
    }
}