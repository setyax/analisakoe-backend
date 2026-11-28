package id.analisakoe.web.ui;

import id.analisakoe.context.ContextService;
import id.analisakoe.core.menu.MenuService;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/ui/hr/employee-requests")
public class UiEmployeeRequestController {

    private final EntityManager em;
    private final MenuService menuService;
    private final ContextService context;

    public UiEmployeeRequestController(EntityManager em, MenuService menuService, ContextService context) {
        this.em = em;
        this.menuService = menuService;
        this.context = context;
    }

    private void commonModel(HttpSession session, Model model, String title) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", title);
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
    }

    private void referenceData(Model model, LocalDate today) {
        model.addAttribute("employees", em.createNativeQuery(
                "SELECT id, nip || ' - ' || name FROM hr.employee " +
                "WHERE tenant_id = :tenantId AND company_id = :compId AND status = 'ACTIVE' ORDER BY name")
            .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
            .getResultList());
        model.addAttribute("items", em.createNativeQuery(
                "SELECT id, code || ' - ' || name FROM md.item " +
                "WHERE tenant_id = :tenantId AND deleted_at IS NULL ORDER BY name")
            .setParameter("tenantId", context.tenantId())
            .getResultList());
        model.addAttribute("uoms", em.createNativeQuery(
                "SELECT id, code FROM md.uom WHERE tenant_id = :tenantId ORDER BY code")
            .setParameter("tenantId", context.tenantId())
            .getResultList());
        model.addAttribute("today", today);
    }

    private Object[] ownedHeader(Long id) {
        try {
            return (Object[]) em.createNativeQuery(
                    "SELECT id, doc_no, doc_date, employee_id, reason, status FROM hr.employee_request_hdr " +
                    "WHERE id = :id AND tenant_id = :tenantId AND company_id = :compId")
                .setParameter("id", id)
                .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                .getSingleResult();
        } catch (jakarta.persistence.NoResultException e) {
            throw new AccessDeniedException("Employee Request tidak ditemukan atau di luar company aktif.");
        }
    }

    private String docNo() {
        return "ER-" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
    }

    @GetMapping
    public String list(HttpSession session, Model model) {
        commonModel(session, model, "Employee Requests");
        List<Object[]> rows = em.createNativeQuery(
                "SELECT h.id, h.doc_no, COALESCE(to_char(h.doc_date, 'DD-MM-YYYY'), '-'), e.nip, e.name, h.reason, h.status " +
                "FROM hr.employee_request_hdr h JOIN hr.employee e ON e.id = h.employee_id " +
                "WHERE h.tenant_id = :tenantId AND h.company_id = :compId ORDER BY h.id DESC")
            .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
            .getResultList();
        model.addAttribute("requests", rows);
        return "ui/hr/employee_request_list";
    }

    @GetMapping("/new")
    public String newForm(HttpSession session, Model model) {
        commonModel(session, model, "New Employee Request");
        referenceData(model, LocalDate.now());
        model.addAttribute("isNew", true);
        return "ui/hr/employee_request_form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, HttpSession session, Model model) {
        commonModel(session, model, "Edit Employee Request");
        Object[] h = ownedHeader(id);
        referenceData(model, h[2] instanceof java.sql.Date sd ? sd.toLocalDate() : LocalDate.now());

        Map<String, Object> req = new HashMap<>();
        req.put("id", h[0]); req.put("docNo", h[1]);
        req.put("docDate", h[2] instanceof java.sql.Date sd ? sd.toLocalDate() : null);
        req.put("employeeId", h[3]); req.put("reason", h[4]); req.put("status", h[5]);
        model.addAttribute("req", req);
        model.addAttribute("isNew", false);

        List<Object[]> details = em.createNativeQuery(
                "SELECT d.item_id, d.uom_id, d.qty, COALESCE(d.short_text,''), " +
                "COALESCE(d.delivery_date::text, '') FROM hr.employee_request_dtl d " +
                "WHERE d.header_id = :id ORDER BY d.line_no")
            .setParameter("id", id).getResultList();
        StringBuilder json = new StringBuilder("[");
        for (Object[] d : details) {
            if (json.length() > 1) json.append(',');
            json.append("{\"itemId\":").append(d[0])
                .append(",\"uomId\":").append(d[1])
                .append(",\"qty\":").append(d[2])
                .append(",\"shortText\":\"").append(escape(d[3])).append("\"")
                .append(",\"deliveryDate\":\"").append(d[4]).append("\"}");
        }
        json.append(']');
        model.addAttribute("prefillLines", json.toString());
        return "ui/hr/employee_request_form";
    }

    private String escape(Object value) {
        return value == null ? "" : value.toString().replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @PostMapping("/save")
    @Transactional
    public String save(@RequestParam(required = false) Long id,
                       @RequestParam LocalDate docDate,
                       @RequestParam Long employeeId,
                       @RequestParam(required = false) String reason,
                       @RequestParam(value = "itemId", required = false) List<Long> itemIds,
                       @RequestParam(value = "uomId", required = false) List<Long> uomIds,
                       @RequestParam(value = "qty", required = false) List<BigDecimal> qtys,
                       @RequestParam(value = "shortText", required = false) List<String> shortTexts,
                       @RequestParam(value = "deliveryDate", required = false) List<String> deliveryDates,
                       RedirectAttributes ra) {
        try {
            if (itemIds == null || itemIds.isEmpty()) throw new IllegalArgumentException("Minimal satu item harus diisi.");
            Long validEmployee = ((Number) em.createNativeQuery(
                    "SELECT id FROM hr.employee WHERE id = :id AND tenant_id = :tenantId AND company_id = :compId AND status = 'ACTIVE'")
                .setParameter("id", employeeId)
                .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                .getSingleResult()).longValue();

            Long headerId;
            if (id == null) {
                headerId = ((Number) em.createNativeQuery(
                        "INSERT INTO hr.employee_request_hdr (tenant_id, company_id, doc_no, doc_date, employee_id, reason, status) " +
                        "VALUES (:tenantId, :compId, :docNo, :docDate, :employeeId, :reason, 'DRAFT') RETURNING id")
                    .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                    .setParameter("docNo", docNo()).setParameter("docDate", docDate)
                    .setParameter("employeeId", validEmployee).setParameter("reason", reason)
                    .getSingleResult()).longValue();
            } else {
                ownedHeader(id);
                em.createNativeQuery(
                        "UPDATE hr.employee_request_hdr SET doc_date = :docDate, employee_id = :employeeId, " +
                        "reason = :reason, updated_at = NOW() " +
                        "WHERE id = :id AND tenant_id = :tenantId AND company_id = :compId AND status = 'DRAFT'")
                    .setParameter("docDate", docDate).setParameter("employeeId", validEmployee)
                    .setParameter("reason", reason).setParameter("id", id)
                    .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                    .executeUpdate();
                em.createNativeQuery("DELETE FROM hr.employee_request_dtl WHERE header_id = :id")
                    .setParameter("id", id).executeUpdate();
                headerId = id;
            }

            for (int i = 0; i < itemIds.size(); i++) {
                String dd = (deliveryDates != null && i < deliveryDates.size() && deliveryDates.get(i) != null && !deliveryDates.get(i).isEmpty())
                        ? deliveryDates.get(i) : docDate.toString();
                em.createNativeQuery(
                        "INSERT INTO hr.employee_request_dtl (header_id, line_no, item_id, uom_id, qty, short_text, delivery_date) " +
                        "VALUES (:headerId, :lineNo, :itemId, :uomId, :qty, :shortText, :deliveryDate)")
                    .setParameter("headerId", headerId).setParameter("lineNo", i + 1)
                    .setParameter("itemId", itemIds.get(i)).setParameter("uomId", uomIds.get(i))
                    .setParameter("qty", qtys.get(i))
                    .setParameter("shortText", shortTexts != null && i < shortTexts.size() ? shortTexts.get(i) : "")
                    .setParameter("deliveryDate", java.sql.Date.valueOf(LocalDate.parse(dd)))
                    .executeUpdate();
            }
            ra.addFlashAttribute("success", id == null ? "Employee Request berhasil dibuat." : "Employee Request berhasil diperbarui.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal: " + e.getMessage());
        }
        return "redirect:/ui/hr/employee-requests";
    }

    @PostMapping("/{id}/status")
    @Transactional
    public String updateStatus(@PathVariable Long id, @RequestParam String status, RedirectAttributes ra) {
        try {
            if (!List.of("SUBMITTED", "APPROVED", "REJECTED", "CANCELLED").contains(status))
                throw new IllegalArgumentException("Status tidak valid.");
            ownedHeader(id);
            em.createNativeQuery(
                    "UPDATE hr.employee_request_hdr SET status = :status, updated_at = NOW() " +
                    "WHERE id = :id AND tenant_id = :tenantId AND company_id = :compId")
                .setParameter("status", status).setParameter("id", id)
                .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                .executeUpdate();
            ra.addFlashAttribute("success", "Status berhasil diubah menjadi " + status + ".");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal: " + e.getMessage());
        }
        return "redirect:/ui/hr/employee-requests";
    }

    @PostMapping("/{id}/delete")
    @Transactional
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            ownedHeader(id);
            em.createNativeQuery("DELETE FROM hr.employee_request_dtl WHERE header_id = :id")
                .setParameter("id", id).executeUpdate();
            em.createNativeQuery(
                    "DELETE FROM hr.employee_request_hdr WHERE id = :id AND tenant_id = :tenantId AND company_id = :compId")
                .setParameter("id", id)
                .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                .executeUpdate();
            ra.addFlashAttribute("success", "Employee Request berhasil dihapus.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal hapus: " + e.getMessage());
        }
        return "redirect:/ui/hr/employee-requests";
    }
}
