package id.analisakoe.web.ui;

import org.springframework.beans.factory.annotation.Autowired;

import id.analisakoe.context.ContextService;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/ui/supplier-portal")
public class UiSupplierPortalController {

    @Autowired
    private ContextService context;

    @PersistenceContext
    private EntityManager em;

    private final MenuService menuService;

    public UiSupplierPortalController(MenuService menuService) {
        this.menuService = menuService;
    }

    private void baseModel(HttpSession session, Model model, String title) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", title);
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
    }

    // ============================================================
    // PORTAL SUPPLIER - DAFTAR SUPPLIER + STATISTIK
    // ============================================================
    @GetMapping
    public String list(@RequestParam(required = false) String q, HttpSession session, Model model) {
        baseModel(session, model, "Portal Supplier");

        String where = "WHERE s.tenant_id = :tenantId AND s.deleted_at IS NULL " +
                       (q != null && !q.isBlank() ? "AND (LOWER(s.name) LIKE LOWER(:q) OR LOWER(s.code) LIKE LOWER(:q)) " : "");

        String sql = "SELECT s.id, s.code, s.name, s.status, s.payment_term_days, s.supplier_group, " +
             "COALESCE((SELECT COUNT(*) FROM pur.purchase_order_hdr po WHERE po.supplier_id = s.id AND po.status NOT IN ('VOID')), 0) AS po_count, " +
             "COALESCE((SELECT COUNT(*) FROM pur.purchase_order_hdr po WHERE po.supplier_id = s.id AND po.status = 'CONFIRMED'), 0) AS po_open, " +
             "COALESCE((SELECT SUM(po.total_amount) FROM pur.purchase_order_hdr po WHERE po.supplier_id = s.id AND po.status NOT IN ('VOID')), 0) AS po_value, " +
             "COALESCE((SELECT COUNT(*) FROM pur.grn_hdr gh JOIN pur.purchase_order_hdr po ON po.id = gh.po_id WHERE po.supplier_id = s.id AND UPPER(TRIM(gh.status)) <> 'VOID'), 0) AS grn_count, " +
             "(SELECT MAX(gh.doc_date) FROM pur.grn_hdr gh JOIN pur.purchase_order_hdr po ON po.id = gh.po_id WHERE po.supplier_id = s.id AND UPPER(TRIM(gh.status)) <> 'VOID') AS last_delivery, " +
             "COALESCE((SELECT SUM(pi.total_amount) FROM pur.purchase_invoice_hdr pi WHERE pi.supplier_id = s.id AND pi.status = 'POSTED'), 0) " +
             " - COALESCE((SELECT SUM(apd.amount) FROM fin.ap_payment_dtl apd " +
             "   JOIN fin.ap_payment aph ON aph.id = apd.payment_id " +
             "   JOIN pur.purchase_invoice_hdr pi2 ON pi2.id = apd.invoice_id " +
             "   WHERE pi2.supplier_id = s.id AND pi2.status = 'POSTED' AND UPPER(TRIM(aph.status)) <> 'VOID'), 0) AS ap_outstanding " +
             "FROM md.supplier s " + where +
             "ORDER BY ap_outstanding DESC, po_value DESC, s.name";

        var query = em.createNativeQuery(sql);
        if (q != null && !q.isBlank()) query.setParameter("q", "%" + q + "%");

        List<Map<String, Object>> suppliers = new ArrayList<>();
        BigDecimal totalPoValue = BigDecimal.ZERO, totalApOutstanding = BigDecimal.ZERO;
        int activeCount = 0, totalOpenPo = 0;
        try {
            for (Object[] row : (List<Object[]>) query.setParameter("tenantId", context.tenantId()).getResultList()) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", row[0]);
                m.put("code", row[1]);
                m.put("name", row[2]);
                m.put("status", row[3]);
                m.put("paymentTerm", row[4] != null ? ((Number) row[4]).intValue() : 0);
                m.put("supplierGroup", row[5] != null ? row[5].toString() : "-");
                m.put("poCount", ((Number) row[6]).longValue());
                m.put("poOpen", ((Number) row[7]).longValue());
                BigDecimal poValue = new BigDecimal(row[8].toString());
                m.put("poValue", poValue);
                m.put("grnCount", ((Number) row[9]).longValue());
                m.put("lastDelivery", row[10] != null ? ((java.sql.Date) row[10]).toLocalDate() : null);
                BigDecimal apOutstanding = new BigDecimal(row[11].toString());
                m.put("apOutstanding", apOutstanding);
                suppliers.add(m);

                totalPoValue = totalPoValue.add(poValue);
                totalApOutstanding = totalApOutstanding.add(apOutstanding);
                if ("ACTIVE".equals(row[3])) activeCount++;
                totalOpenPo += ((Number) row[7]).longValue();
            }
        } catch (Exception e) {
            model.addAttribute("error", "Gagal memuat portal supplier: " + e.getMessage());
        }

        model.addAttribute("q", q);
        model.addAttribute("suppliers", suppliers);
        model.addAttribute("totalPoValue", totalPoValue);
        model.addAttribute("totalApOutstanding", totalApOutstanding);
        model.addAttribute("activeCount", activeCount);
        model.addAttribute("totalOpenPo", totalOpenPo);
        return "ui/crm/supplier_portal";
    }

    // ============================================================
    // DETAIL SUPPLIER
    // ============================================================
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, HttpSession session, Model model) {
        baseModel(session, model, "Portal Supplier - Detail");
        try {
            List<Object[]> supRaw = em.createNativeQuery(
                "SELECT code, name, tax_id, npwp, payment_term_days, supplier_group, status, currency " +
                "FROM md.supplier WHERE id = :id AND deleted_at IS NULL").setParameter("id", id).getResultList();
            if (supRaw.isEmpty()) { model.addAttribute("error", "Supplier tidak ditemukan."); return "redirect:/ui/supplier-portal"; }
            Object[] s = supRaw.get(0);
            model.addAttribute("supCode", s[0]);
            model.addAttribute("supName", s[1]);
            model.addAttribute("supTaxId", s[2]);
            model.addAttribute("supNpwp", s[3]);
            model.addAttribute("supPaymentTerm", s[4] != null ? ((Number) s[4]).intValue() : 0);
            model.addAttribute("supGroup", s[5] != null ? s[5].toString() : "-");
            model.addAttribute("supStatus", s[6]);
            model.addAttribute("supCurrency", s[7]);

            // Purchase Orders
            List<Map<String, Object>> pos = new ArrayList<>();
            for (Object[] row : (List<Object[]>) em.createNativeQuery(
                "SELECT id, doc_no, doc_date, status, total_amount FROM pur.purchase_order_hdr " +
                "WHERE supplier_id = :id AND status NOT IN ('VOID') ORDER BY doc_date DESC, id DESC LIMIT 15")
                .setParameter("id", id).getResultList()) {
                Map<String, Object> m = new HashMap<>();
                m.put("docNo", row[1]); m.put("docDate", row[2] != null ? ((java.sql.Date) row[2]).toLocalDate() : null);
                m.put("status", row[3]); m.put("total", row[4]);
                pos.add(m);
            }
            model.addAttribute("pos", pos);

            // GRN (penerimaan barang)
            List<Map<String, Object>> grns = new ArrayList<>();
            for (Object[] row : (List<Object[]>) em.createNativeQuery(
                "SELECT gh.id, gh.doc_no, gh.doc_date, gh.status, po.doc_no " +
                "FROM pur.grn_hdr gh JOIN pur.purchase_order_hdr po ON po.id = gh.po_id " +
                "WHERE po.supplier_id = :id AND UPPER(TRIM(gh.status)) <> 'VOID' " +
                "ORDER BY gh.doc_date DESC, gh.id DESC LIMIT 10")
                .setParameter("id", id).getResultList()) {
                Map<String, Object> m = new HashMap<>();
                m.put("docNo", row[1]); m.put("docDate", row[2] != null ? ((java.sql.Date) row[2]).toLocalDate() : null);
                m.put("status", row[3]); m.put("poDocNo", row[4]);
                grns.add(m);
            }
            model.addAttribute("grns", grns);

            // Purchase Invoices + status bayar (AP)
            List<Map<String, Object>> invs = new ArrayList<>();
            BigDecimal invTotal = BigDecimal.ZERO, apOutstanding = BigDecimal.ZERO;
            for (Object[] row : (List<Object[]>) em.createNativeQuery(
                "SELECT pi.id, pi.doc_no, pi.invoice_date, pi.status, pi.total_amount, " +
                "COALESCE((SELECT SUM(apd.amount) FROM fin.ap_payment_dtl apd " +
                "  JOIN fin.ap_payment aph ON aph.id = apd.payment_id " +
                "  WHERE apd.invoice_id = pi.id AND UPPER(TRIM(aph.status)) <> 'VOID'), 0) " +
                "FROM pur.purchase_invoice_hdr pi WHERE pi.supplier_id = :id AND pi.status = 'POSTED' " +
                "ORDER BY pi.invoice_date DESC NULLS LAST, pi.id DESC LIMIT 20")
                .setParameter("id", id).getResultList()) {
                Map<String, Object> m = new HashMap<>();
                BigDecimal total = new BigDecimal(row[4].toString());
                BigDecimal paid = new BigDecimal(row[5].toString());
                m.put("docNo", row[1]);
                m.put("invoiceDate", row[2] != null ? ((java.sql.Date) row[2]).toLocalDate() : null);
                m.put("status", row[3]);
                m.put("total", total);
                m.put("paid", paid);
                m.put("outstanding", total.subtract(paid));
                invs.add(m);
                invTotal = invTotal.add(total);
                apOutstanding = apOutstanding.add(total.subtract(paid));
            }
            model.addAttribute("invs", invs);
            model.addAttribute("invTotal", invTotal);
            model.addAttribute("apOutstanding", apOutstanding);

            // AP Payments terakhir
            List<Map<String, Object>> pays = new ArrayList<>();
            BigDecimal payTotal = BigDecimal.ZERO;
            for (Object[] row : (List<Object[]>) em.createNativeQuery(
                "SELECT aph.doc_no, aph.doc_date, apd.amount, pi.doc_no, aph.status " +
                "FROM fin.ap_payment_dtl apd " +
                "JOIN fin.ap_payment aph ON aph.id = apd.payment_id " +
                "JOIN pur.purchase_invoice_hdr pi ON pi.id = apd.invoice_id " +
                "WHERE pi.supplier_id = :id AND UPPER(TRIM(aph.status)) <> 'VOID' " +
                "ORDER BY aph.doc_date DESC, aph.id DESC LIMIT 10")
                .setParameter("id", id).getResultList()) {
                Map<String, Object> m = new HashMap<>();
                BigDecimal amt = new BigDecimal(row[2].toString());
                m.put("docNo", row[0]); m.put("docDate", row[1] != null ? ((java.sql.Date) row[1]).toLocalDate() : null);
                m.put("amount", amt); m.put("invDocNo", row[3]); m.put("status", row[4]);
                pays.add(m);
                payTotal = payTotal.add(amt);
            }
            model.addAttribute("pays", pays);
            model.addAttribute("payTotal", payTotal);

        } catch (Exception e) {
            model.addAttribute("error", "Gagal memuat detail supplier: " + e.getMessage());
        }
        return "ui/crm/supplier_portal_detail";
    }
}
