package id.analisakoe.web.ui;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;



@Controller
@RequestMapping("/ui/print")
public class UiPrintController {

    @PersistenceContext
    private EntityManager em;

    private String fmtDate(Object obj) {
        if (obj == null) return "-";
        try {
            java.time.LocalDate ld = null;
            if (obj instanceof java.sql.Date) ld = ((java.sql.Date) obj).toLocalDate();
            else if (obj instanceof java.sql.Timestamp) ld = ((java.sql.Timestamp) obj).toLocalDateTime().toLocalDate();
            else if (obj instanceof java.time.LocalDate) ld = (java.time.LocalDate) obj;
            else ld = java.time.LocalDate.parse(obj.toString());
            return ld.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        } catch (Exception e) { return obj.toString(); }
    }

    private String safeStr(Object obj, String def) {
        return obj != null ? obj.toString() : def;
    }

    private BigDecimal safeDec(Object obj) {
        if (obj == null) return BigDecimal.ZERO;
        if (obj instanceof BigDecimal) return (BigDecimal) obj;
        try { return new BigDecimal(obj.toString()); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private Map<String, Object> getCompanyInfo(HttpSession session) {
        Map<String, Object> c = new HashMap<>();
        c.put("name", "AnalisaKoe ERP");
        Long compId = 1L;
        try {
            SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
            if (user != null && user.getActiveCompanyId() != null) compId = user.getActiveCompanyId();
        } catch (Exception e) { /* fallback */ }
        try {
            List<?> rows = em.createNativeQuery("SELECT name FROM core.company WHERE id = :id")
                .setParameter("id", compId).getResultList();
            if (!rows.isEmpty()) {
                Object r = rows.get(0);
                if (r instanceof Object[]) r = ((Object[]) r)[0];
                if (r != null) c.put("name", r.toString());
            }
        } catch (Exception e) { /* skip */ }
        return c;
    }

    // ============================================
    // SALES INVOICE
    // ============================================
    @GetMapping("/si/{id}")
    @Transactional(readOnly = true)
    public String printSI(@PathVariable Long id, HttpSession session, Model model) {
        try {
            List<?> hdrRows = em.createNativeQuery(
                "SELECT h.doc_no, h.invoice_date, h.due_date, h.total_amount, h.tax_amount, h.remarks, " +
                "c.name, c.tax_id, h.paid_amount, h.currency, COALESCE(h.tax_type,'NO_TAX'), COALESCE(sp.name, '-') " +
                "FROM sal.sales_invoice_hdr h LEFT JOIN md.customer c ON c.id = h.customer_id " +
                "LEFT JOIN md.salesperson sp ON sp.id = h.salesperson_id WHERE h.id = :id")
                .setParameter("id", id).getResultList();
            if (hdrRows.isEmpty()) return "redirect:/ui/sales/invoices";

            Object[] hdr = (Object[]) hdrRows.get(0);

            model.addAttribute("docNo", safeStr(hdr[0], "-"));
            model.addAttribute("invDate", fmtDate(hdr[1]));
            model.addAttribute("dueDate", fmtDate(hdr[2]));
            model.addAttribute("totalAmount", safeDec(hdr[3]));
            model.addAttribute("taxAmount", safeDec(hdr[4]));
            model.addAttribute("remarks", safeStr(hdr[5], ""));
            model.addAttribute("customerName", safeStr(hdr[6], "-"));
            model.addAttribute("customerTaxId", safeStr(hdr[7], "-"));
            model.addAttribute("paidAmount", safeDec(hdr[8]));
            model.addAttribute("currency", safeStr(hdr[9], "IDR"));
            model.addAttribute("salesperson", safeStr(hdr[11], "-"));

            // Tax Label
            String taxType = safeStr(hdr[10], "NO_TAX");
            String taxLabel = "NO_TAX".equals(taxType) ? "Non-PPN" 
                : ("EXCLUDE".equals(taxType) ? "PPN 11% (Exclude)" : "PPN 11% (Include)");
            model.addAttribute("taxType", taxType);
            model.addAttribute("taxLabel", taxLabel);

            System.out.println("[PRINT-SI] id=" + id + " taxType=" + taxType + " taxLabel=" + taxLabel + " taxAmt=" + hdr[4]);

            List<?> dtlRows = em.createNativeQuery(
                "SELECT i.code, i.name, d.qty, d.price, d.total " +
                "FROM sal.sales_invoice_dtl d JOIN md.item i ON i.id = d.item_id " +
                "WHERE d.header_id = :id ORDER BY d.id").setParameter("id", id).getResultList();

            List<Map<String, Object>> items = new ArrayList<>();
            for (Object r : dtlRows) {
                Object[] rr = (Object[]) r;
                Map<String, Object> m = new HashMap<>();
                m.put("code", safeStr(rr[0], "-"));
                m.put("name", safeStr(rr[1], "-"));
                m.put("qty", safeDec(rr[2]));
                m.put("price", safeDec(rr[3]));
                m.put("total", safeDec(rr[4]));
                items.add(m);
            }
            model.addAttribute("items", items);
            model.addAttribute("company", getCompanyInfo(session));
        } catch (Exception e) {
            System.out.println("[PRINT-SI] ERROR: " + e.getMessage());
            return "redirect:/ui/sales/invoices";
        }
        return "ui/print/sales_invoice";
    }

    // ============================================
    // SALES ORDER (Print SO)
    // ============================================
    @GetMapping("/so/{id}")
    @Transactional(readOnly = true)
    public String printSO(@PathVariable Long id, HttpSession session, Model model) {
        try {
            List<?> hdrRows = em.createNativeQuery(
                "SELECT h.doc_no, h.doc_date, h.total_amount, h.currency, COALESCE(h.remarks,''), c.name, " +
                "COALESCE(h.tax_type,'NO_TAX'), COALESCE(h.tax_amount,0), COALESCE(h.status,'DRAFT'), " +
                "COALESCE(c.billing_address,'-'), COALESCE(c.phone,'-'), COALESCE(sp.name,'-') " +
                "FROM sal.sales_order_hdr h LEFT JOIN md.customer c ON c.id = h.customer_id " +
                "LEFT JOIN md.salesperson sp ON sp.id = h.salesperson_id WHERE h.id = :id")
                .setParameter("id", id).getResultList();
            if (hdrRows.isEmpty()) return "redirect:/ui/sales/orders";

            Object[] hdr = (Object[]) hdrRows.get(0);
            model.addAttribute("docNo", safeStr(hdr[0], "-"));
            model.addAttribute("docDate", fmtDate(hdr[1]));
            model.addAttribute("totalAmount", safeDec(hdr[2]));
            model.addAttribute("currency", safeStr(hdr[3], "IDR"));
            model.addAttribute("remarks", safeStr(hdr[4], ""));
            model.addAttribute("customerName", safeStr(hdr[5], "-"));

            String taxType = safeStr(hdr[6], "NO_TAX");
            String taxLabel = "NO_TAX".equals(taxType) ? "Non-PPN"
                : ("EXCLUDE".equals(taxType) ? "PPN 11% (Exclude)" : "PPN 11% (Include)");
            model.addAttribute("taxType", taxType);
            model.addAttribute("taxLabel", taxLabel);
            model.addAttribute("taxAmount", safeDec(hdr[7]));
            model.addAttribute("status", safeStr(hdr[8], "-"));
            model.addAttribute("customerAddress", safeStr(hdr[9], "-"));
            model.addAttribute("customerPhone", safeStr(hdr[10], "-"));
            model.addAttribute("salesperson", safeStr(hdr[11], "-"));

            List<?> dtlRows = em.createNativeQuery(
                "SELECT i.code, i.name, d.qty, u.code, d.price, (d.qty * d.price) AS total " +
                "FROM sal.sales_order_dtl d JOIN md.item i ON i.id = d.item_id " +
                "LEFT JOIN md.uom u ON u.id = d.uom_id " +
                "WHERE d.header_id = :id ORDER BY d.id").setParameter("id", id).getResultList();

            List<Map<String, Object>> items = new ArrayList<>();
            for (Object r : dtlRows) {
                Object[] rr = (Object[]) r;
                Map<String, Object> m = new HashMap<>();
                m.put("code", safeStr(rr[0], "-"));
                m.put("name", safeStr(rr[1], "-"));
                m.put("qty", safeDec(rr[2]));
                m.put("uom", safeStr(rr[3], "-"));
                m.put("price", safeDec(rr[4]));
                m.put("total", safeDec(rr[5]));
                items.add(m);
            }
            model.addAttribute("items", items);
            model.addAttribute("company", getCompanyInfo(session));
        } catch (Exception e) {
            System.out.println("[printSO] ERROR: " + e.getMessage());
            return "redirect:/ui/sales/orders";
        }
        return "ui/print/sales_order";
    }

    // ============================================
    // PURCHASE INVOICE
    // ============================================
    @GetMapping("/pi/{id}")
    public String printPI(@PathVariable Long id, HttpSession session, Model model) {
        try {
            List<?> hdrRows = em.createNativeQuery(
                "SELECT h.doc_no, h.invoice_date, h.due_date, h.total_amount, h.supplier_invoice_no, h.remarks, " +
                "s.name, h.currency, COALESCE(h.tax_amount,0), COALESCE(h.tax_type,'NO_TAX') " +
                "FROM pur.purchase_invoice_hdr h LEFT JOIN md.supplier s ON s.id = h.supplier_id WHERE h.id = :id")
                .setParameter("id", id).getResultList();
            if (hdrRows.isEmpty()) return "redirect:/ui/purchasing/invoices";

            Object[] hdr = (Object[]) hdrRows.get(0);

            model.addAttribute("docNo", safeStr(hdr[0], "-"));
            model.addAttribute("invDate", fmtDate(hdr[1]));
            model.addAttribute("dueDate", fmtDate(hdr[2]));
            model.addAttribute("totalAmount", safeDec(hdr[3]));
            model.addAttribute("supplierInvNo", safeStr(hdr[4], "-"));
            model.addAttribute("remarks", safeStr(hdr[5], ""));
            model.addAttribute("supplierName", safeStr(hdr[6], "-"));
            model.addAttribute("currency", safeStr(hdr[7], "IDR"));
            model.addAttribute("taxAmount", safeDec(hdr[8]));

            String taxType = safeStr(hdr[9], "NO_TAX");
            String taxLabel = "NO_TAX".equals(taxType) ? "Non-PPN"
                : ("EXCLUDE".equals(taxType) ? "PPN 11% (Exclude)" : "PPN 11% (Include)");
            model.addAttribute("taxType", taxType);
            model.addAttribute("taxLabel", taxLabel);

            List<?> dtlRows = em.createNativeQuery(
                "SELECT i.code, i.name, d.qty, d.price, d.total " +
                "FROM pur.purchase_invoice_dtl d JOIN md.item i ON i.id = d.item_id " +
                "WHERE d.header_id = :id ORDER BY d.id").setParameter("id", id).getResultList();

            List<Map<String, Object>> items = new ArrayList<>();
            for (Object r : dtlRows) {
                Object[] rr = (Object[]) r;
                Map<String, Object> m = new HashMap<>();
                m.put("code", safeStr(rr[0], "-"));
                m.put("name", safeStr(rr[1], "-"));
                m.put("qty", safeDec(rr[2]));
                m.put("price", safeDec(rr[3]));
                m.put("total", safeDec(rr[4]));
                items.add(m);
            }
            model.addAttribute("items", items);
            model.addAttribute("company", getCompanyInfo(session));
        } catch (Exception e) {
            System.out.println("[printPI] ERROR: " + e.getMessage());
            return "redirect:/ui/purchasing/invoices";
        }
        return "ui/print/purchase_invoice";
    }

    // ============================================
    // DELIVERY ORDER (Surat Jalan)
    // ============================================
    @GetMapping("/do/{id}")
    @Transactional(readOnly = true)
    public String printDO(@PathVariable Long id, HttpSession session, Model model) {
        try {
            List<?> hdrRows = em.createNativeQuery(
                "SELECT h.doc_no, h.doc_date, h.remarks, c.name, so.doc_no " +
                "FROM sal.delivery_order_hdr h " +
                "LEFT JOIN md.customer c ON c.id = h.customer_id " +
                "LEFT JOIN sal.sales_order_hdr so ON so.id = h.so_id WHERE h.id = :id")
                .setParameter("id", id).getResultList();
            if (hdrRows.isEmpty()) return "redirect:/ui/sales/delivery-orders";

            Object[] hdr = (Object[]) hdrRows.get(0);

            model.addAttribute("docNo", safeStr(hdr[0], "-"));
            model.addAttribute("docDate", fmtDate(hdr[1]));
            model.addAttribute("remarks", safeStr(hdr[2], ""));
            model.addAttribute("customerName", safeStr(hdr[3], "-"));
            model.addAttribute("soNo", safeStr(hdr[4], "-"));

            List<?> dtlRows = em.createNativeQuery(
                "SELECT i.code, i.name, d.qty, u.code " +
                "FROM sal.delivery_order_dtl d " +
                "JOIN md.item i ON i.id = d.item_id " +
                "LEFT JOIN md.uom u ON u.id = d.uom_id " +
                "WHERE d.header_id = :id ORDER BY d.id").setParameter("id", id).getResultList();

            List<Map<String, Object>> items = new ArrayList<>();
            for (Object r : dtlRows) {
                Object[] rr = (Object[]) r;
                Map<String, Object> m = new HashMap<>();
                m.put("code", safeStr(rr[0], "-"));
                m.put("name", safeStr(rr[1], "-"));
                m.put("qty", safeDec(rr[2]));
                m.put("uom", safeStr(rr[3], "-"));
                items.add(m);
            }
            model.addAttribute("items", items);
            model.addAttribute("company", getCompanyInfo(session));
        } catch (Exception e) {
            return "redirect:/ui/sales/delivery-orders";
        }
        return "ui/print/delivery_order";
    }

    // ============================================
    // PURCHASE ORDER
    // ============================================
    @GetMapping("/po/{id}")
    public String printPO(@PathVariable Long id, HttpSession session, Model model) {
        try {
            List<?> hdrRows = em.createNativeQuery(
                "SELECT h.doc_no, h.doc_date, h.total_amount, h.currency, h.remarks, s.name, " +
                "COALESCE(h.tax_type,'NO_TAX'), COALESCE(h.tax_amount,0) " +
                "FROM pur.purchase_order_hdr h LEFT JOIN md.supplier s ON s.id = h.supplier_id WHERE h.id = :id")
                .setParameter("id", id).getResultList();
            if (hdrRows.isEmpty()) return "redirect:/ui/purchasing/po";

            Object[] hdr = (Object[]) hdrRows.get(0);

            model.addAttribute("docNo", safeStr(hdr[0], "-"));
            model.addAttribute("docDate", fmtDate(hdr[1]));
            model.addAttribute("totalAmount", safeDec(hdr[2]));
            model.addAttribute("currency", safeStr(hdr[3], "IDR"));
            model.addAttribute("remarks", safeStr(hdr[4], ""));
            model.addAttribute("supplierName", safeStr(hdr[5], "-"));
            model.addAttribute("taxAmount", safeDec(hdr[7]));

            String taxType = safeStr(hdr[6], "NO_TAX");
            String taxLabel = "NO_TAX".equals(taxType) ? "Non-PPN"
                : ("EXCLUDE".equals(taxType) ? "PPN 11% (Exclude)" : "PPN 11% (Include)");
            model.addAttribute("taxType", taxType);
            model.addAttribute("taxLabel", taxLabel);

            List<?> dtlRows = em.createNativeQuery(
                "SELECT i.code, i.name, d.qty, d.price, (d.qty * d.price) AS total " +
                "FROM pur.purchase_order_dtl d JOIN md.item i ON i.id = d.item_id " +
                "WHERE d.header_id = :id ORDER BY d.id").setParameter("id", id).getResultList();

            List<Map<String, Object>> items = new ArrayList<>();
            for (Object r : dtlRows) {
                Object[] rr = (Object[]) r;
                Map<String, Object> m = new HashMap<>();
                m.put("code", safeStr(rr[0], "-"));
                m.put("name", safeStr(rr[1], "-"));
                m.put("qty", safeDec(rr[2]));
                m.put("price", safeDec(rr[3]));
                m.put("total", safeDec(rr[4]));
                items.add(m);
            }
            model.addAttribute("items", items);
            model.addAttribute("company", getCompanyInfo(session));
        } catch (Exception e) {
            System.out.println("[printPO] ERROR: " + e.getMessage());
            return "redirect:/ui/purchasing/po";
        }
        return "ui/print/purchase_order";
    }
	
	    // ============================================
    // PICKING LIST
    // ============================================
    @GetMapping("/picking/{id}")
    @Transactional(readOnly = true)
    public String printPicking(@PathVariable Long id, HttpSession session, Model model) {
        try {
            List<?> hdrRows = em.createNativeQuery(
                "SELECT d.doc_no, d.doc_date, COALESCE(c.name, '-'), " +
                "COALESCE(so.doc_no, '-'), COALESCE(d.remarks, '') " +
                "FROM sal.delivery_order_hdr d " +
                "LEFT JOIN md.customer c ON c.id = d.customer_id " +
                "LEFT JOIN sal.sales_order_hdr so ON so.id = d.so_id " +
                "WHERE d.id = :id").setParameter("id", id).getResultList();
            if (hdrRows.isEmpty()) return "redirect:/ui/sales/delivery-orders";

            Object[] hdr = (Object[]) hdrRows.get(0);
            model.addAttribute("docNo", safeStr(hdr[0], "-"));
            model.addAttribute("docDate", fmtDate(hdr[1]));
            model.addAttribute("customerName", safeStr(hdr[2], "-"));
            model.addAttribute("soNo", safeStr(hdr[3], "-"));
            model.addAttribute("remarks", safeStr(hdr[4], ""));

            List<?> dtlRows = em.createNativeQuery(
                "SELECT i.code, i.name, dd.qty, COALESCE(u.code, '-') " +
                "FROM sal.delivery_order_dtl dd " +
                "JOIN md.item i ON i.id = dd.item_id " +
                "LEFT JOIN md.uom u ON u.id = dd.uom_id " +
                "WHERE dd.header_id = :id ORDER BY dd.id").setParameter("id", id).getResultList();

            List<Map<String, Object>> items = new ArrayList<>();
            for (Object r : dtlRows) {
                Object[] rr = (Object[]) r;
                Map<String, Object> m = new HashMap<>();
                m.put("code", safeStr(rr[0], "-"));
                m.put("name", safeStr(rr[1], "-"));
                m.put("qty", safeDec(rr[2]));
                m.put("uom", safeStr(rr[3], "-"));
                items.add(m);
            }
            model.addAttribute("items", items);
            model.addAttribute("company", getCompanyInfo(session));
        } catch (Exception e) {
            System.out.println("[printPicking] ERROR: " + e.getMessage());
            return "redirect:/ui/sales/delivery-orders";
        }
        return "ui/print/picking_list";
    }

    // ============================================
    // LOAD SHEET
    // ============================================
    @GetMapping("/loadsheet/{id}")
    @Transactional(readOnly = true)
    public String printLoadSheet(@PathVariable Long id, HttpSession session, Model model) {
        try {
            List<?> hdrRows = em.createNativeQuery(
                "SELECT d.doc_no, d.doc_date, COALESCE(c.name, '-'), " +
                "COALESCE(so.doc_no, '-'), COALESCE(d.remarks, '') " +
                "FROM sal.delivery_order_hdr d " +
                "LEFT JOIN md.customer c ON c.id = d.customer_id " +
                "LEFT JOIN sal.sales_order_hdr so ON so.id = d.so_id " +
                "WHERE d.id = :id").setParameter("id", id).getResultList();
            if (hdrRows.isEmpty()) return "redirect:/ui/sales/delivery-orders";

            Object[] hdr = (Object[]) hdrRows.get(0);
            model.addAttribute("docNo", safeStr(hdr[0], "-"));
            model.addAttribute("docDate", fmtDate(hdr[1]));
            model.addAttribute("customerName", safeStr(hdr[2], "-"));
            model.addAttribute("soNo", safeStr(hdr[3], "-"));
            model.addAttribute("remarks", safeStr(hdr[4], ""));

            List<?> dtlRows = em.createNativeQuery(
                "SELECT i.code, i.name, dd.qty, COALESCE(u.code, '-') " +
                "FROM sal.delivery_order_dtl dd " +
                "JOIN md.item i ON i.id = dd.item_id " +
                "LEFT JOIN md.uom u ON u.id = dd.uom_id " +
                "WHERE dd.header_id = :id ORDER BY dd.id").setParameter("id", id).getResultList();

            List<Map<String, Object>> items = new ArrayList<>();
            BigDecimal totalQty = BigDecimal.ZERO;
            for (Object r : dtlRows) {
                Object[] rr = (Object[]) r;
                Map<String, Object> m = new HashMap<>();
                m.put("code", safeStr(rr[0], "-"));
                m.put("name", safeStr(rr[1], "-"));
                BigDecimal qty = safeDec(rr[2]);
                m.put("qty", qty);
                m.put("uom", safeStr(rr[3], "-"));
                items.add(m);
                totalQty = totalQty.add(qty);
            }
            model.addAttribute("items", items);
            model.addAttribute("totalQty", totalQty);
            model.addAttribute("company", getCompanyInfo(session));
        } catch (Exception e) {
            System.out.println("[printLoadSheet] ERROR: " + e.getMessage());
            return "redirect:/ui/sales/delivery-orders";
        }
        return "ui/print/load_sheet";
    }
	
	    // ============================================
    // PICKING LIST — MULTI DO (Consolidated)
    // ============================================
    @GetMapping("/picking-batch")
    @Transactional(readOnly = true)
    public String printPickingBatch(@RequestParam("ids") List<Long> ids, HttpSession session, Model model) {
        if (ids == null || ids.isEmpty()) return "redirect:/ui/sales/delivery-orders";

        try {
            // 1. Ambil header DO yang terpilih
            String sqlDos = "SELECT d.id, d.doc_no, d.doc_date, COALESCE(c.name, '-') " +
                            "FROM sal.delivery_order_hdr d " +
                            "LEFT JOIN md.customer c ON c.id = d.customer_id " +
                            "WHERE d.id IN (:ids) ORDER BY d.id";
            List<Object[]> dosRaw = em.createNativeQuery(sqlDos)
                .setParameter("ids", ids).getResultList();
            
            List<Map<String, Object>> dos = new ArrayList<>();
            for (Object[] row : dosRaw) {
                Map<String, Object> m = new HashMap<>();
                m.put("docNo", safeStr(row[1], "-"));
                m.put("docDate", fmtDate(row[2]));
                m.put("customer", safeStr(row[3], "-"));
                dos.add(m);
            }
            model.addAttribute("dos", dos);

            // 2. Ambil item dan GABUNGKAN (SUM) Qty-nya
            String sqlItems = "SELECT i.code, i.name, SUM(dd.qty) as total_qty, COALESCE(u.code, '-') " +
                              "FROM sal.delivery_order_dtl dd " +
                              "JOIN md.item i ON i.id = dd.item_id " +
                              "LEFT JOIN md.uom u ON u.id = dd.uom_id " +
                              "WHERE dd.header_id IN (:ids) " +
                              "GROUP BY i.code, i.name, u.code " +
                              "ORDER BY i.code";
            List<Object[]> itemsRaw = em.createNativeQuery(sqlItems)
                .setParameter("ids", ids).getResultList();

            List<Map<String, Object>> items = new ArrayList<>();
            for (Object[] row : itemsRaw) {
                Map<String, Object> m = new HashMap<>();
                m.put("code", safeStr(row[0], "-"));
                m.put("name", safeStr(row[1], "-"));
                m.put("qty", safeDec(row[2]));
                m.put("uom", safeStr(row[3], "-"));
                items.add(m);
            }
            model.addAttribute("items", items);
            model.addAttribute("company", getCompanyInfo(session));
            
        } catch (Exception e) {
            return "redirect:/ui/sales/delivery-orders";
        }
        return "ui/print/picking_batch";
    }

    // ============================================
    // LOAD SHEET — MULTI DO
    // ============================================
    @GetMapping("/loadsheet-batch")
    @Transactional(readOnly = true)
    public String printLoadSheetBatch(@RequestParam("ids") List<Long> ids, HttpSession session, Model model) {
        if (ids == null || ids.isEmpty()) return "redirect:/ui/sales/delivery-orders";

        try {
            // Ambil header DO
            String sqlDos = "SELECT d.id, d.doc_no, d.doc_date, COALESCE(c.name, '-'), COALESCE(d.remarks, '') " +
                            "FROM sal.delivery_order_hdr d " +
                            "LEFT JOIN md.customer c ON c.id = d.customer_id " +
                            "WHERE d.id IN (:ids) ORDER BY d.id";
            List<Object[]> dosRaw = em.createNativeQuery(sqlDos)
                .setParameter("ids", ids).getResultList();

            List<Map<String, Object>> dos = new ArrayList<>();
            for (Object[] row : dosRaw) {
                Map<String, Object> m = new HashMap<>();
                Long doId = ((Number) row[0]).longValue();
                m.put("docNo", safeStr(row[1], "-"));
                m.put("docDate", fmtDate(row[2]));
                m.put("customer", safeStr(row[3], "-"));
                m.put("remarks", safeStr(row[4], ""));

                // Ambil item per DO
                String sqlDtl = "SELECT i.code, i.name, dd.qty, COALESCE(u.code, '-') " +
                                "FROM sal.delivery_order_dtl dd " +
                                "JOIN md.item i ON i.id = dd.item_id " +
                                "LEFT JOIN md.uom u ON u.id = dd.uom_id " +
                                "WHERE dd.header_id = :doId ORDER BY dd.id";
                List<Object[]> dtlRaw = em.createNativeQuery(sqlDtl)
                    .setParameter("doId", doId).getResultList();
                
                List<Map<String, Object>> items = new ArrayList<>();
                for (Object[] r : dtlRaw) {
                    Map<String, Object> im = new HashMap<>();
                    im.put("code", safeStr(r[0], "-"));
                    im.put("name", safeStr(r[1], "-"));
                    im.put("qty", safeDec(r[2]));
                    im.put("uom", safeStr(r[3], "-"));
                    items.add(im);
                }
                m.put("items", items);
                dos.add(m);
            }
            model.addAttribute("dos", dos);
            model.addAttribute("company", getCompanyInfo(session));

        } catch (Exception e) {
            return "redirect:/ui/sales/delivery-orders";
        }
        return "ui/print/loadsheet_batch";
    }
	
	    // ============================================
    // API: Get Drivers (untuk dropdown loadsheet)
    // ============================================
    @GetMapping("/api/drivers")
    @ResponseBody
    public List<Map<String, Object>> getDrivers() {
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            List<Object[]> rows = em.createNativeQuery(
                "SELECT id, name, COALESCE(phone, '-'), COALESCE(license_no, '-') " +
                "FROM md.driver WHERE tenant_id = 1 AND status = 'ACTIVE' ORDER BY name")
                .getResultList();
            for (Object[] r : rows) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", r[0]);
                m.put("name", r[1] != null ? r[1].toString() : "");
                m.put("phone", r[2]);
                m.put("licenseNo", r[3]);
                list.add(m);
            }
        } catch (Exception e) {
            System.err.println("[getDrivers] Error: " + e.getMessage());
        }
        return list;
    }

    // ============================================
    // API: Get Vehicles (untuk dropdown loadsheet)
    // ============================================
    @GetMapping("/api/vehicles")
    @ResponseBody
    public List<Map<String, Object>> getVehicles() {
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            List<Object[]> rows = em.createNativeQuery(
                "SELECT id, plate_no, COALESCE(vehicle_type, '-'), " +
                "COALESCE(capacity_kg, 0), COALESCE(driver_id, 0) " +
                "FROM md.vehicle WHERE tenant_id = 1 AND status = 'ACTIVE' ORDER BY plate_no")
                .getResultList();
            for (Object[] r : rows) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", r[0]);
                m.put("plateNo", r[1] != null ? r[1].toString() : "");
                m.put("vehicleType", r[2]);
                m.put("capacityKg", r[3]);
                m.put("driverId", r[4]);
                list.add(m);
            }
        } catch (Exception e) {
            System.err.println("[getVehicles] Error: " + e.getMessage());
        }
        return list;
    }
	
	    // ============================================
    // PRINT SALES QUOTATION (SPH)
    // ============================================
    @GetMapping("/quotation/{id}")
    public String printQuotation(@PathVariable Long id, HttpSession session, Model model) {
        try {
            List<?> hdrRows = em.createNativeQuery(
                "SELECT q.doc_no, q.doc_date, q.valid_until, " +
                "COALESCE(c.name, '-'), COALESCE(c.tax_id, '-'), " +
                "q.currency, q.remarks, q.total_amount, q.tax_amount " +
                "FROM sal.sales_quotation_hdr q " +
                "LEFT JOIN md.customer c ON c.id = q.customer_id " +
                "WHERE q.id = :id").setParameter("id", id).getResultList();
            
            if (hdrRows.isEmpty()) return "redirect:/ui/sales/quotations";

            Object[] hdr = (Object[]) hdrRows.get(0);
            model.addAttribute("docNo", safeStr(hdr[0], "-"));
            model.addAttribute("docDate", fmtDate(hdr[1]));
            model.addAttribute("validUntil", fmtDate(hdr[2]));
            model.addAttribute("customerName", safeStr(hdr[3], "-"));
            model.addAttribute("customerTaxId", safeStr(hdr[4], "-"));
            model.addAttribute("currency", safeStr(hdr[5], "IDR"));
            model.addAttribute("remarks", safeStr(hdr[6], ""));
            model.addAttribute("totalAmount", safeDec(hdr[7]));
            model.addAttribute("taxAmount", safeDec(hdr[8]));

            List<?> dtlRows = em.createNativeQuery(
                "SELECT i.code, i.name, d.qty, u.code, d.price, d.subtotal, d.tax_amount, d.total " +
                "FROM sal.sales_quotation_dtl d " +
                "JOIN md.item i ON i.id = d.item_id " +
                "LEFT JOIN md.uom u ON u.id = d.uom_id " +
                "WHERE d.header_id = :id ORDER BY d.id").setParameter("id", id).getResultList();

            List<Map<String, Object>> items = new ArrayList<>();
            for (Object r : dtlRows) {
                Object[] rr = (Object[]) r;
                Map<String, Object> m = new HashMap<>();
                m.put("code", safeStr(rr[0], "-"));
                m.put("name", safeStr(rr[1], "-"));
                m.put("qty", safeDec(rr[2]));
                m.put("uom", safeStr(rr[3], "-"));
                m.put("price", safeDec(rr[4]));
                m.put("subtotal", safeDec(rr[5]));
                m.put("tax", safeDec(rr[6]));
                m.put("total", safeDec(rr[7]));
                items.add(m);
            }
            model.addAttribute("items", items);
            model.addAttribute("company", getCompanyInfo(session));
        } catch (Exception e) {
            System.err.println("[printQuotation] ERROR: " + e.getMessage());
            e.printStackTrace();
            return "redirect:/ui/sales/quotations";
        }
        return "ui/print/quotation_print";
    }
	
}
