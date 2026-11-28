package id.analisakoe.web.ui;
import java.util.Map;
import java.util.List;

import id.analisakoe.context.ContextService;
import id.analisakoe.core.menu.MenuService;
import id.analisakoe.web.ui.SessionInterceptor;
import id.analisakoe.web.ui.SessionUser;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Controller
@RequestMapping("/ui/purchasing/invoices")
public class UiPurchaseInvoiceController {

    private final MenuService menuService;

    @Autowired
    private EntityManager em;

    @Autowired
    private ContextService context;

    @Autowired
    private id.analisakoe.core.service.NotificationService notificationService;

    private void notif(SessionUser user, String type, String title, String message, String link) {
        try {
            if (user != null) {
                notificationService.notifyAllUsers(context.tenantId(),
                    user.getId(), user.getUsername(), type, title, message, link);
            }
        } catch (Exception ignored) { }
    }

    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @Autowired
    private id.analisakoe.pur.service.PurchaseInvoiceService invoiceService;

    public UiPurchaseInvoiceController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping
    public String list(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Purchase Invoices");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        String sql = "SELECT h.id, h.doc_no, h.invoice_date, COALESCE(s.name, '-') AS supplier_name, " +
                     "COALESCE(h.currency, 'IDR') AS currency, COALESCE(h.total_amount, 0) AS total_amount, " +
                     "h.due_date, COALESCE(h.supplier_invoice_no, '-') AS faktur_supplier, " +
                     "COALESCE(h.remarks, '') AS remarks, h.status, COALESCE(h.tax_amount, 0) " +
                     "FROM pur.purchase_invoice_hdr h " +
                     "LEFT JOIN md.supplier s ON h.supplier_id = s.id " +
                     "WHERE h.tenant_id = :tenantId AND h.company_id = :companyId ORDER BY h.id DESC";

        List<Map<String, Object>> invoiceList = new ArrayList<>();
        try {
            List<Object[]> results = em.createNativeQuery(sql)
                .setParameter("tenantId", context.tenantId())
                .setParameter("companyId", context.companyId())
                .getResultList();
            for (Object[] row : results) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", row[0]);
                map.put("docNo", row[1] != null ? row[1].toString() : "-");
                Object invDate = row[2];
                map.put("invoiceDate", invDate != null ? ((java.sql.Date) invDate).toLocalDate() : null);
                map.put("supplierName", row[3]);
                map.put("currency", row[4]);
                map.put("totalAmount", row[5]);
                Object dueDate = row[6];
                map.put("dueDate", dueDate != null ? ((java.sql.Date) dueDate).toLocalDate() : null);
                map.put("fakturSupplier", row[7]);
                map.put("remarks", row[8]);
                map.put("status", row[9]);
                map.put("taxAmount", row[10]);
                invoiceList.add(map);
            }
        } catch (Exception e) {
            model.addAttribute("error", "Gagal memuat data: " + e.getMessage());
        }
        model.addAttribute("invoices", invoiceList);
        return "ui/purchasing/invoice_list";
    }

    @GetMapping("/new")
    public String newForm(@RequestParam(required = false) Long grnId, HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Buat Purchase Invoice");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("selectedGrnId", grnId);
        model.addAttribute("isNew", true);

        String sql = "SELECT g.id, g.doc_no, g.doc_date, COALESCE(s.name, '-'), COALESCE(g.po_id, 0), s.payment_term_days " +
                     "FROM pur.grn_hdr g " +
                     "LEFT JOIN pur.purchase_order_hdr po ON g.po_id = po.id " +
                     "LEFT JOIN md.supplier s ON po.supplier_id = s.id " +
                     "WHERE g.tenant_id = :tenantId AND g.company_id = :companyId AND g.status = 'CONFIRMED' " +
                     "  AND NOT EXISTS (SELECT 1 FROM pur.purchase_invoice_hdr i WHERE i.grn_id = g.id AND i.status <> 'VOID') " +
                     "ORDER BY g.id DESC";

        List<Map<String, Object>> grnList = new ArrayList<>();
        try {
            List<Object[]> results = em.createNativeQuery(sql)
                .setParameter("tenantId", context.tenantId())
                .setParameter("companyId", context.companyId())
                .getResultList();
            for (Object[] row : results) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", row[0]);
                map.put("docNo", row[1]);
                Object dateObj = row[2];
                map.put("docDate", dateObj != null ? ((java.sql.Date) dateObj).toLocalDate() : null);
                map.put("supplierName", row[3]);
                map.put("paymentTermDays", row[5] != null ? ((Number) row[5]).intValue() : 0);
                grnList.add(map);
            }
        } catch (Exception e) {
            model.addAttribute("error", "Gagal memuat GRN: " + e.getMessage());
        }
        model.addAttribute("grns", grnList);
        return "ui/purchasing/invoice_form";
    }

    @PostMapping
    @Transactional
    public String save(@RequestParam Long grnId,
                       @RequestParam String supplierInvoiceNo,
                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate invoiceDate,
                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDate,
                       @RequestParam(required = false) String remarks,
                       HttpSession session,
                       RedirectAttributes ra) {
        SessionUser notifUser = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        try {
            Object[] grn = (Object[]) em.createNativeQuery(
                "SELECT g.tenant_id, g.company_id, po.supplier_id, g.status, po.tax_type " +
                "FROM pur.grn_hdr g JOIN pur.purchase_order_hdr po ON po.id = g.po_id " +
                "WHERE g.id = :id").setParameter("id", grnId).getSingleResult();

            Long tenantId   = ((Number) grn[0]).longValue();
            Long companyId  = grn[1] == null ? null : ((Number) grn[1]).longValue();
            Long supplierId = grn[2] == null ? null : ((Number) grn[2]).longValue();
            String grnStatus = (String) grn[3];
            String taxType = grn[4] != null ? grn[4].toString() : "NO_TAX";

            if (!"CONFIRMED".equals(grnStatus)) throw new IllegalStateException("GRN belum CONFIRMED.");
            if (companyId == null)  throw new IllegalStateException("PO belum punya company_id.");
            if (supplierId == null) throw new IllegalStateException("PO belum punya supplier_id.");

            Long existing = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM pur.purchase_invoice_hdr WHERE grn_id = :g AND status <> 'VOID'")
                .setParameter("g", grnId).getSingleResult()).longValue();
            if (existing > 0) throw new IllegalStateException("GRN ini sudah ditagih.");

            String period = invoiceDate.toString().substring(0, 7).replace("-", "/");
            Long seq = ((Number) em.createNativeQuery(
                "SELECT COALESCE(MAX(CAST(SPLIT_PART(doc_no,'/',4) AS INTEGER)),0)+1 " +
                "FROM pur.purchase_invoice_hdr WHERE company_id = :c AND doc_no LIKE :p")
                .setParameter("c", companyId).setParameter("p", "INV/" + period + "/%")
                .getSingleResult()).longValue();
            String docNo = String.format("INV/%s/%04d", period, seq);

                        List<Object[]> grnDetails = em.createNativeQuery(
                "SELECT gd.item_id, gd.qty_received, " +
                "COALESCE((SELECT MAX(pod.price) FROM pur.purchase_order_dtl pod " +
                "  WHERE pod.header_id = gh.po_id AND pod.item_id = gd.item_id), 0) AS price, " +
                "gd.uom_id " +
                "FROM pur.grn_dtl gd " +
                "JOIN pur.grn_hdr gh ON gh.id = gd.header_id " +
                "WHERE gd.header_id = :grn").setParameter("grn", grnId).getResultList();

            BigDecimal grandTotal = BigDecimal.ZERO;
            BigDecimal totalTax = BigDecimal.ZERO;

            for (Object[] d : grnDetails) {
                BigDecimal qty = new BigDecimal(d[1].toString());
                BigDecimal price = new BigDecimal(d[2].toString());
                BigDecimal subtotal = qty.multiply(price);
                BigDecimal total = subtotal;

                if ("EXCLUDE".equals(taxType)) {
                    BigDecimal ppn = subtotal.multiply(new BigDecimal("0.11"));
                    total = subtotal.add(ppn);
                    totalTax = totalTax.add(ppn);
                } else if ("INCLUDE".equals(taxType)) {
                    BigDecimal ppn = subtotal.multiply(new BigDecimal("11").divide(new BigDecimal("111"), 2, RoundingMode.HALF_UP));
                    totalTax = totalTax.add(ppn);
                }
                grandTotal = grandTotal.add(total);
            }
			
			            // ============================================================
            // 3-WAY MATCH: Cek Total Qty Invoice ≤ Qty GRN untuk PO yang sama
            // ============================================================
            Long poId = ((Number) em.createNativeQuery(
                "SELECT po_id FROM pur.grn_hdr WHERE id = :grnId")
                .setParameter("grnId", grnId).getSingleResult()).longValue();

            // Ambil semua item dari GRN ini
            List<Object[]> grnItems = em.createNativeQuery(
                "SELECT gd.item_id, gd.qty_received FROM pur.grn_dtl gd WHERE gd.header_id = :grnId")
                .setParameter("grnId", grnId).getResultList();

            for (Object[] gi : grnItems) {
                Long itemId = ((Number) gi[0]).longValue();
                BigDecimal qtyGrn = new BigDecimal(gi[1].toString());

                // Total qty PO untuk item ini
                List<?> poQtyList = em.createNativeQuery(
                    "SELECT COALESCE(qty, 0) FROM pur.purchase_order_dtl " +
                    "WHERE header_id = :poId AND item_id = :itemId")
                    .setParameter("poId", poId).setParameter("itemId", itemId).getResultList();
                BigDecimal poQty = poQtyList.isEmpty() ? BigDecimal.ZERO 
                    : new BigDecimal(poQtyList.get(0).toString());

                // Total qty invoice LAMA untuk item ini (dari GRN lain di PO yang sama)
                                BigDecimal invQtyOther = BigDecimal.ZERO;
                Object otherInvList = em.createNativeQuery(
                    "SELECT COALESCE(SUM(pid.qty), 0) FROM pur.purchase_invoice_dtl pid " +
                    "JOIN pur.purchase_invoice_hdr pih ON pih.id = pid.header_id " +
                    "JOIN pur.grn_hdr gh ON gh.id = pih.grn_id " +
                    "WHERE gh.po_id = :poId AND pid.item_id = :itemId " +
                    "AND pih.grn_id <> :grnId AND UPPER(TRIM(pih.status)) <> 'VOID'")
                    .setParameter("poId", poId).setParameter("itemId", itemId).setParameter("grnId", grnId)
                    .getSingleResult();
                if (otherInvList != null) {
                    invQtyOther = new BigDecimal(otherInvList.toString());
                }

                BigDecimal totalInv = invQtyOther.add(qtyGrn);
                if (totalInv.compareTo(poQty) > 0) {
                    throw new RuntimeException(
                        "❌ OVER-INVOICE: Total Qty Invoice untuk item ID " + itemId + " (" + totalInv + ") " +
                        "melebihi Qty PO (" + poQty + "). " +
                        "Sudah ditagih sebelumnya: " + invQtyOther + ", invoice ini: " + qtyGrn + ".");
                }
            }
            // ============================================================
			
            Long newId = ((Number) em.createNativeQuery(
                "INSERT INTO pur.purchase_invoice_hdr " +
                "(tenant_id, company_id, doc_no, supplier_invoice_no, invoice_date, due_date, " +
                " supplier_id, grn_id, status, remarks, total_amount, tax_type, tax_amount) " +
                "VALUES (:tenant, :company, :doc, :sin, :inv, :due, :sup, :grn, 'DRAFT', :rem, :total, :taxType, :taxAmt) RETURNING id")
                .setParameter("tenant", tenantId).setParameter("company", companyId)
                .setParameter("doc", docNo).setParameter("sin", supplierInvoiceNo)
                .setParameter("inv", java.sql.Date.valueOf(invoiceDate)).setParameter("due", java.sql.Date.valueOf(dueDate))
                .setParameter("sup", supplierId).setParameter("grn", grnId).setParameter("rem", remarks)
                .setParameter("total", grandTotal).setParameter("taxType", taxType).setParameter("taxAmt", totalTax)
                .getSingleResult()).longValue();

            for (Object[] d : grnDetails) {
                Long itemId = ((Number) d[0]).longValue();
                BigDecimal qty = new BigDecimal(d[1].toString());
                BigDecimal price = new BigDecimal(d[2].toString());
                BigDecimal subtotal = qty.multiply(price);
                BigDecimal total = subtotal;

                if ("EXCLUDE".equals(taxType)) {
                    total = subtotal.add(subtotal.multiply(new BigDecimal("0.11")));
                }

                em.createNativeQuery("INSERT INTO pur.purchase_invoice_dtl (header_id, item_id, uom_id, qty, price, total) VALUES (:inv, :item, :uom, :qty, :price, :total)")
                    .setParameter("inv", newId).setParameter("item", itemId).setParameter("uom", d.length > 3 && d[3] != null ? ((Number) d[3]).longValue() : null)
                    .setParameter("qty", qty).setParameter("price", price).setParameter("total", total)
                    .executeUpdate();
            }

            ra.addFlashAttribute("success", "Purchase Invoice " + docNo + " berhasil dibuat (DRAFT).");
            notif(notifUser, "INV", "Invoice " + docNo + " dibuat",
                "Purchase Invoice " + docNo + " baru telah dibuat dari GRN (DRAFT).",
                "/api/ui/purchasing/invoices");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal: " + e.getMessage());
            return "redirect:/ui/purchasing/invoices/new?grnId=" + grnId;
        }
        return "redirect:/ui/purchasing/invoices";
    }

    @GetMapping("/{id}/view")
    public String viewInv(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId();
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "View Purchase Invoice");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        try {
            List<Object[]> hdrRaw = em.createNativeQuery(
                "SELECT h.doc_no, h.invoice_date, h.due_date, s.name, h.currency, h.total_amount, " +
                "h.tax_amount, h.remarks, h.status, h.supplier_invoice_no, COALESCE(h.tax_type, 'NO_TAX') " +
                "FROM pur.purchase_invoice_hdr h LEFT JOIN md.supplier s ON s.id = h.supplier_id " +
                "WHERE h.id = :id AND h.tenant_id = :tenantId AND h.company_id = :compId")
                .setParameter("id", id)
                .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                .getResultList();
            if (hdrRaw.isEmpty()) {
                ra.addFlashAttribute("error", "Invoice tidak ditemukan.");
                return "redirect:/ui/purchasing/invoices";
            }
            Object[] h = hdrRaw.get(0);
            model.addAttribute("docNo", h[0]);
            model.addAttribute("invoiceDate", h[1] != null ? ((java.sql.Date) h[1]).toLocalDate() : null);
            model.addAttribute("dueDate", h[2] != null ? ((java.sql.Date) h[2]).toLocalDate() : null);
            model.addAttribute("supplierName", h[3]);
            model.addAttribute("currency", h[4] != null ? h[4].toString() : "IDR");
            model.addAttribute("totalAmount", h[5]);
            model.addAttribute("taxAmount", h[6]);
            model.addAttribute("remarks", h[7]);
            model.addAttribute("status", h[8]);
            model.addAttribute("fakturSupplier", h[9]);

            String taxType = h[10] != null ? h[10].toString() : "NO_TAX";
            model.addAttribute("taxType", taxType);
            model.addAttribute("taxLabel", "NO_TAX".equals(taxType) ? "Non-PPN"
                : ("EXCLUDE".equals(taxType) ? "PPN 11% (Exclude)" : "PPN 11% (Include)"));

            List<Object[]> dtlRows = em.createNativeQuery(
                "SELECT d.id, i.code, i.name, d.qty, u.code, d.price, d.total " +
                "FROM pur.purchase_invoice_dtl d " +
                "LEFT JOIN md.item i ON i.id = d.item_id " +
                "LEFT JOIN md.uom u ON u.id = d.uom_id " +
                "WHERE d.header_id = :id ORDER BY d.id").setParameter("id", id).getResultList();

            List<Map<String, Object>> lines = new ArrayList<>();
            java.math.BigDecimal subtotal = java.math.BigDecimal.ZERO;
            for (Object r : dtlRows) {
                Object[] rr = (Object[]) r;
                Map<String, Object> m = new HashMap<>();
                m.put("itemCode", rr[1] != null ? rr[1].toString() : "-");
                m.put("itemName", rr[2] != null ? rr[2].toString() : "-");
                m.put("qty", rr[3] != null ? new java.math.BigDecimal(rr[3].toString()).stripTrailingZeros().toPlainString() : "0");
                m.put("uomCode", rr[4] != null ? rr[4].toString() : "-");
                m.put("price", rr[5] != null ? new java.math.BigDecimal(rr[5].toString()) : java.math.BigDecimal.ZERO);
                m.put("total", rr[6] != null ? new java.math.BigDecimal(rr[6].toString()) : java.math.BigDecimal.ZERO);
                subtotal = subtotal.add((java.math.BigDecimal) m.get("total"));
                lines.add(m);
            }
            model.addAttribute("lines", lines);
            model.addAttribute("subtotal", subtotal);
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal memuat Invoice: " + e.getMessage());
            return "redirect:/ui/purchasing/invoices";
        }
        return "ui/purchasing/invoice_view";
    }

    @GetMapping("/{id}/edit")
    public String editInv(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Edit Purchase Invoice");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("isNew", false);
        model.addAttribute("invoiceId", id);

        try {
            List<Object[]> hdrRaw = em.createNativeQuery(
                "SELECT doc_no, supplier_invoice_no, invoice_date, due_date, grn_id, remarks, status, tax_type, tax_amount " +
                "FROM pur.purchase_invoice_hdr WHERE id = :id AND tenant_id = :tenantId AND company_id = :compId")
                .setParameter("id", id)
                .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                .getResultList();
            
            if (hdrRaw.isEmpty()) {
                ra.addFlashAttribute("error", "Invoice tidak ditemukan.");
                return "redirect:/ui/purchasing/invoices";
            }
            Object[] hdr = hdrRaw.get(0);
            String status = hdr[6] != null ? hdr[6].toString() : "";
            if (!"DRAFT".equals(status)) {
                ra.addFlashAttribute("error", "Hanya Invoice berstatus DRAFT yang bisa diedit. Status: " + status);
                return "redirect:/ui/purchasing/invoices";
            }

            model.addAttribute("invoiceDocNo", hdr[0]);
            model.addAttribute("supplierInvoiceNo", hdr[1]);
            model.addAttribute("invoiceDate", ((java.sql.Date) hdr[2]).toLocalDate());
            model.addAttribute("dueDate", ((java.sql.Date) hdr[3]).toLocalDate());
            Long grnId = ((Number) hdr[4]).longValue();
            model.addAttribute("selectedGrnId", grnId);
            model.addAttribute("remarks", hdr[5]);
            model.addAttribute("isNew", false);

            // Daftar GRN untuk dropdown: GRN CONFIRMED yang belum di-invoice,
            // PLUS GRN milik invoice yang sedang diedit ini.
            String sql = "SELECT g.id, g.doc_no, g.doc_date, COALESCE(s.name, '-'), COALESCE(g.po_id, 0), s.payment_term_days " +
                         "FROM pur.grn_hdr g " +
                         "LEFT JOIN pur.purchase_order_hdr po ON g.po_id = po.id " +
                         "LEFT JOIN md.supplier s ON po.supplier_id = s.id " +
                         "WHERE g.tenant_id = :tenantId AND g.company_id = :companyId AND g.status = 'CONFIRMED' " +
                         "  AND (NOT EXISTS (SELECT 1 FROM pur.purchase_invoice_hdr i WHERE i.grn_id = g.id AND i.status <> 'VOID') " +
                         "       OR g.id = :grnId) " +
                         "ORDER BY g.id DESC";

            List<Map<String, Object>> grnList = new ArrayList<>();
            List<Object[]> results = em.createNativeQuery(sql)
                .setParameter("grnId", grnId)
                .setParameter("tenantId", context.tenantId())
                .setParameter("companyId", context.companyId())
                .getResultList();
            for (Object[] row : results) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", row[0]);
                map.put("docNo", row[1]);
                Object dateObj = row[2];
                map.put("docDate", dateObj != null ? ((java.sql.Date) dateObj).toLocalDate() : null);
                map.put("supplierName", row[3]);
                map.put("paymentTermDays", row[5] != null ? ((Number) row[5]).intValue() : 0);
                grnList.add(map);
            }
            model.addAttribute("grns", grnList);
            model.addAttribute("today", LocalDate.now());

        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal memuat Invoice: " + e.getMessage());
            return "redirect:/ui/purchasing/invoices";
        }
        return "ui/purchasing/invoice_form";
    }

    @PostMapping("/{id}")
    @Transactional
    public String update(@PathVariable Long id,
                         @RequestParam Long grnId,
                         @RequestParam String supplierInvoiceNo,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate invoiceDate,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDate,
                         @RequestParam(required = false) String remarks,
                         RedirectAttributes ra) {
        try {
            List<?> statusList = em.createNativeQuery("SELECT status FROM pur.purchase_invoice_hdr "
                + "WHERE id = :id AND tenant_id = :tenantId AND company_id = :compId")
                .setParameter("id", id)
                .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                .getResultList();
            String status = statusList.isEmpty() ? "" : statusList.get(0).toString();
            if (!"DRAFT".equals(status)) {
                ra.addFlashAttribute("error", "Hanya DRAFT yang bisa diubah.");
                return "redirect:/ui/purchasing/invoices";
            }

            Object[] grn = (Object[]) em.createNativeQuery(
                "SELECT po.supplier_id, po.tax_type FROM pur.grn_hdr g JOIN pur.purchase_order_hdr po ON po.id = g.po_id "
                + "WHERE g.id = :id AND g.tenant_id = :tenantId AND g.company_id = :compId")
                .setParameter("id", grnId)
                .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                .getSingleResult();
            String taxType = grn[1] != null ? grn[1].toString() : "NO_TAX";

            List<Object[]> grnDetails = em.createNativeQuery(
    "SELECT gd.item_id, gd.qty_received, " +
    "COALESCE((SELECT MAX(pod.price) FROM pur.purchase_order_dtl pod WHERE pod.header_id = gh.po_id AND pod.item_id = gd.item_id), 0) AS price, " +
    "gd.uom_id " +
    "FROM pur.grn_dtl gd " +
    "JOIN pur.grn_hdr gh ON gh.id = gd.header_id " +
    "WHERE gd.header_id = :grn").setParameter("grn", grnId).getResultList();

            BigDecimal grandTotal = BigDecimal.ZERO;
            BigDecimal totalTax = BigDecimal.ZERO;

            for (Object[] d : grnDetails) {
                BigDecimal qty = new BigDecimal(d[1].toString());
                BigDecimal price = new BigDecimal(d[2].toString());
                BigDecimal subtotal = qty.multiply(price);
                BigDecimal total = subtotal;

                if ("EXCLUDE".equals(taxType)) {
                    BigDecimal ppn = subtotal.multiply(new BigDecimal("0.11"));
                    total = subtotal.add(ppn);
                    totalTax = totalTax.add(ppn);
                } else if ("INCLUDE".equals(taxType)) {
                    BigDecimal ppn = subtotal.multiply(new BigDecimal("11").divide(new BigDecimal("111"), 2, RoundingMode.HALF_UP));
                    totalTax = totalTax.add(ppn);
                }
                grandTotal = grandTotal.add(total);
            }

            em.createNativeQuery("UPDATE pur.purchase_invoice_hdr SET grn_id = :grn, supplier_invoice_no = :sin, " +
                                 "invoice_date = :inv, due_date = :due, remarks = :rem, total_amount = :total, tax_type = :taxType, tax_amount = :taxAmt "
                                 + "WHERE id = :id AND tenant_id = :tenantId AND company_id = :compId")
                .setParameter("grn", grnId).setParameter("sin", supplierInvoiceNo)
                .setParameter("inv", java.sql.Date.valueOf(invoiceDate)).setParameter("due", java.sql.Date.valueOf(dueDate))
                .setParameter("rem", remarks).setParameter("total", grandTotal)
                .setParameter("taxType", taxType).setParameter("taxAmt", totalTax)
                .setParameter("id", id)
                .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                .executeUpdate();

            em.createNativeQuery("DELETE FROM pur.purchase_invoice_dtl WHERE header_id = :id").setParameter("id", id).executeUpdate();

            for (Object[] d : grnDetails) {
                Long itemId = ((Number) d[0]).longValue();
                BigDecimal qty = new BigDecimal(d[1].toString());
                BigDecimal price = new BigDecimal(d[2].toString());
                BigDecimal subtotal = qty.multiply(price);
                BigDecimal total = subtotal;

                if ("EXCLUDE".equals(taxType)) {
                    total = subtotal.add(subtotal.multiply(new BigDecimal("0.11")));
                }

                em.createNativeQuery("INSERT INTO pur.purchase_invoice_dtl (header_id, item_id, uom_id, qty, price, total) VALUES (:inv, :item, :uom, :qty, :price, :total)")
                    .setParameter("inv", id).setParameter("item", itemId).setParameter("uom", d.length > 3 && d[3] != null ? ((Number) d[3]).longValue() : null)
                    .setParameter("qty", qty).setParameter("price", price).setParameter("total", total).executeUpdate();
            }

            ra.addFlashAttribute("success", "Purchase Invoice berhasil diperbarui.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal update Invoice: " + e.getMessage());
            return "redirect:/ui/purchasing/invoices/" + id + "/edit";
        }
        return "redirect:/ui/purchasing/invoices";
    }

    @GetMapping("/api/grn-details/{id}")
    @ResponseBody
    public List<Map<String, Object>> getGrnDetails(@PathVariable Long id) {
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            String sql = "SELECT d.item_id, i.code, i.name, d.qty_received AS qty, d.uom_id, u.code AS uom_code, " +
                         "COALESCE(pod.price, 0) AS price, COALESCE(po.tax_type, 'NO_TAX') AS tax_type " +
                         "FROM pur.grn_dtl d " +
                         "JOIN pur.grn_hdr gh ON gh.id = d.header_id " +
                         "JOIN pur.purchase_order_hdr po ON po.id = gh.po_id " +
                         "LEFT JOIN pur.purchase_order_dtl pod ON pod.header_id = gh.po_id AND pod.item_id = d.item_id " +
                         "LEFT JOIN md.item i ON i.id = d.item_id " +
                         "LEFT JOIN md.uom u ON u.id = d.uom_id " +
                         "WHERE d.header_id = :grnId ORDER BY d.id";
            List<Object[]> rows = em.createNativeQuery(sql).setParameter("grnId", id).getResultList();
            for (Object[] row : rows) {
                Map<String, Object> map = new HashMap<>();
                map.put("itemId", row[0]);
                map.put("itemCode", row[1] != null ? row[1].toString() : "");
                map.put("itemName", row[2] != null ? row[2].toString() : "");
                map.put("qty", row[3]);
                map.put("uomId", row[4]);
                map.put("uomCode", row[5] != null ? row[5].toString() : "");
                map.put("price", row[6]);
                map.put("taxType", row[7] != null ? row[7].toString() : "NO_TAX");
                list.add(map);
            }
        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getMessage());
            list.add(err);
        }
        return list;
    }

    @PostMapping("/{id}/confirm")
    public String confirm(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        SessionUser notifUser = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        try {
            transactionTemplate.executeWithoutResult(tx -> invoiceService.postInvoice(id));
            ra.addFlashAttribute("success", "Purchase Invoice berhasil di-post. Hutang Usaha telah diakui (jurnal dibuat).");
            List<?> dn = em.createNativeQuery("SELECT doc_no FROM pur.purchase_invoice_hdr "
                + "WHERE id = :id AND tenant_id = :tenantId AND company_id = :compId")
                .setParameter("id", id)
                .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                .getResultList();
            String docNo = dn.isEmpty() ? "INV#" + id : dn.get(0).toString();
            notif(notifUser, "INV", "Invoice " + docNo + " di-post",
                "Purchase Invoice " + docNo + " telah di-post, hutang usaha diakui.",
                "/api/ui/purchasing/invoices");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal post PI: " + e.getMessage());
        }
        return "redirect:/ui/purchasing/invoices";
    }

    @PostMapping("/{id}/unconfirm")
    public String unconfirm(@PathVariable Long id, RedirectAttributes ra) {
        try {
            transactionTemplate.executeWithoutResult(tx -> invoiceService.unpostInvoice(id));
            ra.addFlashAttribute("success", "Purchase Invoice berhasil di-unpost. Jurnal hutang dibalik, kembali ke DRAFT.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal unpost PI: " + e.getMessage());
        }
        return "redirect:/ui/purchasing/invoices";
    }

        @PostMapping("/{id}/void")
    @Transactional
    public String voidInv(@PathVariable Long id, RedirectAttributes ra) {
        try {
            // 1. Cek status invoice
            List<?> statusList = em.createNativeQuery(
                "SELECT status, paid_amount, doc_no, tenant_id, company_id FROM pur.purchase_invoice_hdr "
                + "WHERE id = :id AND tenant_id = :tenantId AND company_id = :compId")
                .setParameter("id", id)
                .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                .getResultList();

            if (statusList.isEmpty()) {
                throw new RuntimeException("Invoice tidak ditemukan.");
            }

            Object[] row = (Object[]) statusList.get(0);
            String status = row[0] != null ? row[0].toString() : "";
            BigDecimal paid = row[1] != null ? new BigDecimal(row[1].toString()) : BigDecimal.ZERO;
            String docNo = row[2] != null ? row[2].toString() : "?";
            final Long tenantId = ((Number) row[3]).longValue();
            final Long compId = ((Number) row[4]).longValue();

            // 2. VALIDASI: PAID / PARTIAL tidak bisa void
            if ("PAID".equals(status)) {
                throw new RuntimeException(
                    "❌ Invoice " + docNo + " sudah LUNAS (PAID). Tidak bisa di-void. " +
                    "Void payment pembayaran terlebih dahulu jika perlu.");
            }
            if ("PARTIAL".equals(status)) {
                throw new RuntimeException(
                    "❌ Invoice " + docNo + " sudah dibayar sebagian (PARTIAL: Rp " + paid + "). " +
                    "Void payment pembayaran terlebih dahulu.");
            }
            if ("VOID".equals(status)) {
                throw new RuntimeException("Invoice " + docNo + " sudah VOID.");
            }

            // 3. Cek paid_amount > 0
            if (paid.compareTo(BigDecimal.ZERO) > 0) {
                throw new RuntimeException(
                    "❌ Invoice " + docNo + " memiliki pembayaran (Rp " + paid + "). " +
                    "Tidak bisa di-void sebelum payment di-void.");
            }

            // 4. Cek apakah ada payment aktif
            Long activePayments = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM fin.ap_payment_dtl pd " +
                "JOIN fin.cash_bank_trx cbtr ON cbtr.id = pd.payment_id " +
                "WHERE pd.invoice_id = :id AND UPPER(TRIM(cbtr.status)) <> 'VOID'")
                .setParameter("id", id).getSingleResult()).longValue();

            if (activePayments > 0) {
                throw new RuntimeException(
                    "❌ Invoice " + docNo + " punya " + activePayments + " payment aktif. " +
                    "Void payment terlebih dahulu sebelum void invoice.");
            }

            // 5. Lakukan void
            em.createNativeQuery(
                "UPDATE pur.purchase_invoice_hdr SET status = 'VOID' WHERE id = :id "
                + "AND tenant_id = :tenantId AND company_id = :compId")
                .setParameter("id", id)
                .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                .executeUpdate();

            // 6. Reverse jurnal PURCHASE
            try {
                Object[] jvInfo = (Object[]) em.createNativeQuery(
                    "SELECT id FROM fin.journal_hdr WHERE ref_type = 'PURCHASE_INVOICE' AND ref_id = :id AND status = 'POSTED' "
                    + "AND tenant_id = :tenantId AND company_id = :compId")
                    .setParameter("id", id)
                    .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                    .getSingleResult();

                Long origJvId = ((Number) jvInfo[0]).longValue();
                List<Object[]> jvLines = em.createNativeQuery(
                    "SELECT account_id, debit, credit FROM fin.journal_dtl WHERE header_id = :hdr")
                    .setParameter("hdr", origJvId).getResultList();

                String revDocNo = "REV-VOID-PI-" + id;
                Long revJvId = ((Number) em.createNativeQuery(
                    "INSERT INTO fin.journal_hdr " +
                    "(tenant_id, company_id, doc_no, doc_date, journal_type, remarks, status, ref_type, ref_id) " +
                    "VALUES (:tenantId, :compId, :docNo, CURRENT_DATE, 'VOID_PURCHASE', :remarks, 'POSTED', 'PURCHASE_INVOICE_VOID', :id) RETURNING id")
                    .setParameter("tenantId", tenantId).setParameter("compId", compId)
                    .setParameter("docNo", revDocNo)
                    .setParameter("remarks", "Void journal for PI " + docNo)
                    .setParameter("id", id)
                    .getSingleResult()).longValue();

                for (Object[] line : jvLines) {
                    Long accId = ((Number) line[0]).longValue();
                    BigDecimal dr = new BigDecimal(line[1].toString());
                    BigDecimal cr = new BigDecimal(line[2].toString());
                    em.createNativeQuery(
                        "INSERT INTO fin.journal_dtl (header_id, account_id, debit, credit, description) " +
                        "VALUES (:hdr, :acc, :dr, :cr, :desc)")
                        .setParameter("hdr", revJvId).setParameter("acc", accId)
                        .setParameter("dr", cr).setParameter("cr", dr)
                        .setParameter("desc", "Reversal void PI " + docNo).executeUpdate();
                }
            } catch (Exception ignore) { }

            ra.addFlashAttribute("success", "Purchase Invoice " + docNo + " berhasil di-VOID.");
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            ra.addFlashAttribute("error", root.getMessage());
        }
        return "redirect:/ui/purchasing/invoices";
    }

    @PostMapping("/{id}/delete")
    @Transactional
    public String deleteInv(@PathVariable Long id, RedirectAttributes ra) {
        try {
            List<?> statusList = em.createNativeQuery("SELECT status FROM pur.purchase_invoice_hdr "
                + "WHERE id = :id AND tenant_id = :tenantId AND company_id = :compId")
                .setParameter("id", id)
                .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                .getResultList();
            String status = statusList.isEmpty() ? "" : statusList.get(0).toString();
            if (!"DRAFT".equals(status)) {
                ra.addFlashAttribute("error", "Hanya Invoice DRAFT yang bisa dihapus. Status: " + status);
                return "redirect:/ui/purchasing/invoices";
            }
            em.createNativeQuery("DELETE FROM pur.purchase_invoice_dtl WHERE header_id = :id").setParameter("id", id).executeUpdate();
            em.createNativeQuery("DELETE FROM pur.purchase_invoice_hdr WHERE id = :id "
                + "AND tenant_id = :tenantId AND company_id = :compId")
                .setParameter("id", id)
                .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                .executeUpdate();
            ra.addFlashAttribute("success", "Purchase Invoice berhasil dihapus.");
        } catch (Exception e) { ra.addFlashAttribute("error", "Gagal: " + e.getMessage()); }
        return "redirect:/ui/purchasing/invoices";
    }
}