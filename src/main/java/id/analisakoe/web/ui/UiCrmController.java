package id.analisakoe.web.ui;

import id.analisakoe.core.menu.MenuService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/ui/crm")
public class UiCrmController {

    @PersistenceContext
    private EntityManager em;

    private final MenuService menuService;

    public UiCrmController(MenuService menuService) {
        this.menuService = menuService;
    }

    private void baseModel(HttpSession session, Model model, String title) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", title);
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
    }

    // ============================================================
    // CRM DASHBOARD - MONITORING CUSTOMER
    // ============================================================
    @GetMapping
    public String dashboard(@RequestParam(required = false) String q, HttpSession session, Model model) {
        baseModel(session, model, "CRM - Monitoring Customer");

        String where = "WHERE c.tenant_id = 1 AND c.deleted_at IS NULL " +
                       (q != null && !q.isBlank() ? "AND (LOWER(c.name) LIKE LOWER(:q) OR LOWER(c.code) LIKE LOWER(:q)) " : "");

        String sql = "SELECT c.id, c.code, c.name, c.status, c.credit_limit, c.payment_term_days, " +
             "COALESCE((SELECT COUNT(*) FROM sal.sales_order_hdr so WHERE so.customer_id = c.id AND so.status NOT IN ('VOID','CANCELLED')), 0) AS so_count, " +
             "COALESCE((SELECT SUM(so.total_amount) FROM sal.sales_order_hdr so WHERE so.customer_id = c.id AND so.status NOT IN ('VOID','CANCELLED')), 0) AS so_value, " +
             "COALESCE((SELECT SUM(si.total_amount) FROM sal.sales_invoice_hdr si WHERE si.customer_id = c.id AND si.status = 'POSTED'), 0) AS sales_total, " +
             "COALESCE((SELECT SUM(si.total_amount - COALESCE(si.paid_amount, 0)) FROM sal.sales_invoice_hdr si WHERE si.customer_id = c.id AND si.status = 'POSTED'), 0) AS ar_outstanding, " +
             "COALESCE((SELECT SUM(si.total_amount - COALESCE(si.paid_amount, 0)) FROM sal.sales_invoice_hdr si WHERE si.customer_id = c.id AND si.status = 'POSTED' AND si.due_date < CURRENT_DATE), 0) AS ar_overdue, " +
             "(SELECT MAX(si.invoice_date) FROM sal.sales_invoice_hdr si WHERE si.customer_id = c.id AND si.status = 'POSTED') AS last_invoice_date " +
             "FROM md.customer c " + where +
             "ORDER BY ar_overdue DESC, sales_total DESC, c.name";

        var query = em.createNativeQuery(sql);
        if (q != null && !q.isBlank()) query.setParameter("q", "%" + q + "%");

        List<Map<String, Object>> customers = new ArrayList<>();
        BigDecimal totalSales = BigDecimal.ZERO, totalOutstanding = BigDecimal.ZERO,
                   totalOverdue = BigDecimal.ZERO;
        int activeCount = 0, overdueCount = 0;
        try {
            for (Object[] row : (List<Object[]>) query.getResultList()) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", row[0]);
                m.put("code", row[1]);
                m.put("name", row[2]);
                m.put("status", row[3]);
                m.put("creditLimit", row[4] != null ? new BigDecimal(row[4].toString()) : BigDecimal.ZERO);
                m.put("paymentTerm", row[5] != null ? ((Number) row[5]).intValue() : 0);
                m.put("soCount", ((Number) row[6]).longValue());
                m.put("soValue", new BigDecimal(row[7].toString()));
                BigDecimal sales = new BigDecimal(row[8].toString());
                BigDecimal outstanding = new BigDecimal(row[9].toString());
                BigDecimal overdue = new BigDecimal(row[10].toString());
                m.put("salesTotal", sales);
                m.put("arOutstanding", outstanding);
                m.put("arOverdue", overdue);
                m.put("lastInvoiceDate", row[11] != null ? ((java.sql.Date) row[11]).toLocalDate() : null);
                // Pemakaian credit limit %
                if (m.get("creditLimit") != null && ((BigDecimal) m.get("creditLimit")).signum() > 0) {
                    m.put("creditUsedPct", outstanding.multiply(new BigDecimal("100"))
                        .divide((BigDecimal) m.get("creditLimit"), 1, java.math.RoundingMode.HALF_UP));
                } else {
                    m.put("creditUsedPct", null);
                }
                customers.add(m);

                totalSales = totalSales.add(sales);
                totalOutstanding = totalOutstanding.add(outstanding);
                totalOverdue = totalOverdue.add(overdue);
                if ("ACTIVE".equals(row[3])) activeCount++;
                if (overdue.signum() > 0) overdueCount++;
            }
        } catch (Exception e) {
            model.addAttribute("error", "Gagal memuat CRM: " + e.getMessage());
        }

        model.addAttribute("q", q);
        model.addAttribute("customers", customers);
        model.addAttribute("totalSales", totalSales);
        model.addAttribute("totalOutstanding", totalOutstanding);
        model.addAttribute("totalOverdue", totalOverdue);
        model.addAttribute("activeCount", activeCount);
        model.addAttribute("overdueCount", overdueCount);
        return "ui/crm/dashboard";
    }

    // ============================================================
    // DETAIL CUSTOMER
    // ============================================================
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, HttpSession session, Model model) {
        baseModel(session, model, "CRM - Detail Customer");
        try {
            List<Object[]> custRaw = em.createNativeQuery(
                "SELECT code, name, tax_id, npwp, credit_limit, payment_term_days, status, currency " +
                "FROM md.customer WHERE id = :id AND deleted_at IS NULL").setParameter("id", id).getResultList();
            if (custRaw.isEmpty()) { model.addAttribute("error", "Customer tidak ditemukan."); return "redirect:/ui/crm"; }
            Object[] c = custRaw.get(0);
            model.addAttribute("custCode", c[0]);
            model.addAttribute("custName", c[1]);
            model.addAttribute("custTaxId", c[2]);
            model.addAttribute("custNpwp", c[3]);
            model.addAttribute("custCreditLimit", c[4] != null ? new BigDecimal(c[4].toString()) : BigDecimal.ZERO);
            model.addAttribute("custPaymentTerm", c[5] != null ? ((Number) c[5]).intValue() : 0);
            model.addAttribute("custStatus", c[6]);
            model.addAttribute("custCurrency", c[7]);

            // Sales Orders terakhir
            List<Map<String, Object>> sos = new ArrayList<>();
            for (Object[] row : (List<Object[]>) em.createNativeQuery(
                "SELECT id, doc_no, doc_date, status, total_amount FROM sal.sales_order_hdr " +
                "WHERE customer_id = :id AND status NOT IN ('VOID','CANCELLED') ORDER BY doc_date DESC, id DESC LIMIT 15")
                .setParameter("id", id).getResultList()) {
                Map<String, Object> m = new HashMap<>();
                m.put("docNo", row[1]); m.put("docDate", row[2] != null ? ((java.sql.Date) row[2]).toLocalDate() : null);
                m.put("status", row[3]); m.put("total", row[4]);
                sos.add(m);
            }
            model.addAttribute("sos", sos);

            // Sales Invoices + status bayar
            List<Map<String, Object>> invs = new ArrayList<>();
            BigDecimal invTotal = BigDecimal.ZERO, invOutstanding = BigDecimal.ZERO;
            for (Object[] row : (List<Object[]>) em.createNativeQuery(
                "SELECT id, doc_no, invoice_date, due_date, status, total_amount, COALESCE(paid_amount, 0) " +
                "FROM sal.sales_invoice_hdr WHERE customer_id = :id AND status = 'POSTED' " +
                "ORDER BY invoice_date DESC NULLS LAST, id DESC LIMIT 20")
                .setParameter("id", id).getResultList()) {
                Map<String, Object> m = new HashMap<>();
                BigDecimal total = new BigDecimal(row[5].toString());
                BigDecimal paid = new BigDecimal(row[6].toString());
                m.put("docNo", row[1]);
                m.put("invoiceDate", row[2] != null ? ((java.sql.Date) row[2]).toLocalDate() : null);
                m.put("dueDate", row[3] != null ? ((java.sql.Date) row[3]).toLocalDate() : null);
                m.put("status", row[4]);
                m.put("total", total);
                m.put("paid", paid);
                m.put("outstanding", total.subtract(paid));
                LocalDate due = m.get("dueDate") != null ? (LocalDate) m.get("dueDate") : null;
                m.put("overdue", due != null && due.isBefore(LocalDate.now()) && total.subtract(paid).signum() > 0);
                invs.add(m);
                invTotal = invTotal.add(total);
                invOutstanding = invOutstanding.add(total.subtract(paid));
            }
            model.addAttribute("invs", invs);
            model.addAttribute("invTotal", invTotal);
            model.addAttribute("invOutstanding", invOutstanding);

            // AR Collections (pembayaran) terakhir
            List<Map<String, Object>> ars = new ArrayList<>();
            BigDecimal arTotal = BigDecimal.ZERO;
            for (Object[] row : (List<Object[]>) em.createNativeQuery(
                "SELECT id, doc_no, doc_date, amount, status FROM fin.ar_collection " +
                "WHERE customer_id = :id AND status <> 'VOID' ORDER BY doc_date DESC, id DESC LIMIT 10")
                .setParameter("id", id).getResultList()) {
                Map<String, Object> m = new HashMap<>();
                m.put("docNo", row[1]); m.put("docDate", row[2] != null ? ((java.sql.Date) row[2]).toLocalDate() : null);
                BigDecimal amt = new BigDecimal(row[3].toString());
                m.put("amount", amt); m.put("status", row[4]);
                ars.add(m);
                arTotal = arTotal.add(amt);
            }
            model.addAttribute("ars", ars);
            model.addAttribute("arTotal", arTotal);

        } catch (Exception e) {
            model.addAttribute("error", "Gagal memuat detail customer: " + e.getMessage());
        }
        return "ui/crm/customer_detail";
    }
}
