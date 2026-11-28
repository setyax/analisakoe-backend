package id.analisakoe.web.ui;

import id.analisakoe.context.ContextService;
import id.analisakoe.web.ui.SessionInterceptor;
import id.analisakoe.web.ui.SessionUser;

import id.analisakoe.core.menu.MenuService;
import id.analisakoe.core.service.NumberRangeService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/ui/sales/quotations")
public class UiSalesQuotationController {

    @Autowired
    private ContextService context;

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private NumberRangeService numberRangeService;

    private final MenuService menuService;

    public UiSalesQuotationController(MenuService menuService) {
        this.menuService = menuService;
    }

    // ============================================================
    // LIST
    // ============================================================
    @GetMapping
    public String list(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Sales Quotations");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        String sql = "SELECT q.id, q.doc_no, q.doc_date, q.valid_until, q.status, " +
                     "COALESCE(c.name, '-') AS customer_name, " +
                     "q.total_amount, q.currency, q.converted_so_id " +
                     "FROM sal.sales_quotation_hdr q " +
                     "LEFT JOIN md.customer c ON c.id = q.customer_id " +
                     "WHERE q.tenant_id = :tenantId AND q.company_id = :compId ORDER BY q.id DESC";

        List<Map<String, Object>> quotations = new ArrayList<>();
        try {
            for (Object[] row : (List<Object[]>) em.createNativeQuery(sql).setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId()).getResultList()) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", row[0]);
                m.put("docNo", row[1] != null ? row[1].toString() : "-");
                
                Object dateObj = row[2];
                LocalDate docDate = null;
                if (dateObj instanceof java.sql.Date) docDate = ((java.sql.Date) dateObj).toLocalDate();
                else if (dateObj instanceof LocalDate) docDate = (LocalDate) dateObj;
                m.put("docDate", docDate);
                
                Object validObj = row[3];
                LocalDate validUntil = null;
                if (validObj instanceof java.sql.Date) validUntil = ((java.sql.Date) validObj).toLocalDate();
                else if (validObj instanceof LocalDate) validUntil = (LocalDate) validObj;
                m.put("validUntil", validUntil);
                
                m.put("status", row[4] != null ? row[4].toString() : "DRAFT");
                m.put("customerName", row[5]);
                m.put("totalAmount", row[6] != null ? row[6] : BigDecimal.ZERO);
                m.put("currency", row[7] != null ? row[7].toString() : "IDR");
                m.put("convertedSoId", row[8]);
                quotations.add(m);
            }
        } catch (Exception e) {
            model.addAttribute("error", "Gagal memuat Quotation: " + e.getMessage());
        }
        model.addAttribute("quotations", quotations);
        return "ui/sales/quotation_list";
    }

    // ============================================================
    // FORM NEW
    // ============================================================
    @GetMapping("/new")
    public String newForm(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "New Sales Quotation");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("defaultValidUntil", LocalDate.now().plusDays(30));

        // Customers
        List<Map<String, Object>> customers = new ArrayList<>();
        for (Object[] row : (List<Object[]>) em.createNativeQuery(
                "SELECT id, name FROM md.customer WHERE tenant_id = :tenantId AND company_id = :compId ORDER BY name")
                .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId()).getResultList()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", row[0]); m.put("name", row[1]);
            customers.add(m);
        }
        model.addAttribute("customers", customers);

        // Items
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object[] row : (List<Object[]>) em.createNativeQuery(
                "SELECT id, code, name FROM md.item WHERE tenant_id = :tenantId AND deleted_at IS NULL ORDER BY name").setParameter("tenantId", context.tenantId()).getResultList()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", row[0]); m.put("code", row[1]); m.put("name", row[2]);
            items.add(m);
        }
        model.addAttribute("items", items);

        // UOMs
        List<Map<String, Object>> uoms = new ArrayList<>();
        for (Object[] row : (List<Object[]>) em.createNativeQuery(
                "SELECT id, code FROM md.uom WHERE tenant_id = :tenantId ORDER BY code").setParameter("tenantId", context.tenantId()).getResultList()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", row[0]); m.put("code", row[1]);
            uoms.add(m);
        }
        model.addAttribute("uoms", uoms);

        return "ui/sales/quotation_form";
    }
	
	           // ============================================================
    // API: Get Item Price (TANPA customer_id — sesuai schema md.item_price)
    // ============================================================
    @GetMapping("/api/item-price/{itemId}")
    @org.springframework.web.bind.annotation.ResponseBody
    public java.util.Map<String, Object> getItemPrice(@PathVariable Long itemId) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("found", false);
        result.put("price", BigDecimal.ZERO);
        
        try {
            String sql = "SELECT price, currency FROM md.item_price " +
                         "WHERE item_id = :itemId " +
                         "  AND tenant_id = :tenantId " +
                         "  AND (valid_from IS NULL OR valid_from <= CURRENT_DATE) " +
                         "  AND (valid_to IS NULL OR valid_to >= CURRENT_DATE) " +
                         "  AND (status IS NULL OR UPPER(TRIM(status)) = 'ACTIVE') " +
                         "ORDER BY valid_from DESC NULLS LAST, id DESC " +
                         "LIMIT 1";
            
            List<?> rows = em.createNativeQuery(sql)
                .setParameter("itemId", itemId)
                .setParameter("tenantId", context.tenantId()).getResultList();
            
            if (!rows.isEmpty()) {
                Object[] row = (Object[]) rows.get(0);
                BigDecimal price = row[0] != null ? new BigDecimal(row[0].toString()) : BigDecimal.ZERO;
                String currency = row[1] != null ? row[1].toString().trim() : "IDR";
                
                result.put("found", price.compareTo(BigDecimal.ZERO) > 0);
                result.put("price", price);
                result.put("currency", currency);
            } else {
                result.put("message", "Tidak ada harga aktif untuk item ID " + itemId);
            }
        } catch (Exception e) {
            System.err.println("[getItemPrice] ERROR: " + e.getMessage());
            e.printStackTrace();
            result.put("error", e.getMessage());
        }
        return result;
    }

    // ============================================================
    // SAVE
    // ============================================================
    @PostMapping
    @Transactional
    public String save(@RequestParam LocalDate docDate,
                       @RequestParam(required = false) LocalDate validUntil,
                       @RequestParam Long customerId,
                       @RequestParam String currency,
                       @RequestParam String taxType,
                       @RequestParam(required = false) String remarks,
                       @RequestParam("itemId") List<Long> itemIds,
                       @RequestParam("uomId") List<Long> uomIds,
                       @RequestParam("qty") List<BigDecimal> qtys,
                       @RequestParam("price") List<String> prices,
                       HttpSession session,
                       RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        final Long tenantId = context.tenantId();
        final Long compId = context.companyId();
        try {
            if (itemIds == null || itemIds.isEmpty()) {
                throw new RuntimeException("Minimal 1 item harus ada.");
            }

            String docNo = numberRangeService.generate("QUO", "QUO");
            Long hdrId = ((Number) em.createNativeQuery(
                "INSERT INTO sal.sales_quotation_hdr " +
                "(tenant_id, company_id, doc_no, doc_date, valid_until, customer_id, " +
                " currency, remarks, status, total_amount, tax_type, tax_amount, created_at) " +
                "VALUES (:tenantId, :compId, :docNo, :docDate, :validUntil, :custId, " +
                " :currency, :remarks, 'DRAFT', 0, :taxType, 0, NOW()) RETURNING id")
                .setParameter("tenantId", tenantId).setParameter("compId", compId)
                .setParameter("docNo", docNo).setParameter("docDate", docDate)
                .setParameter("validUntil", validUntil).setParameter("custId", customerId)
                .setParameter("currency", currency).setParameter("remarks", remarks)
                .setParameter("taxType", taxType)
                .getSingleResult()).longValue();

            BigDecimal grandTotal = BigDecimal.ZERO;
            BigDecimal totalTax = BigDecimal.ZERO;

            for (int i = 0; i < itemIds.size(); i++) {
                if (qtys.get(i) == null || qtys.get(i).compareTo(BigDecimal.ZERO) <= 0) continue;

                BigDecimal qty = qtys.get(i);
                BigDecimal price = BigDecimal.ZERO;
                String priceStr = prices.get(i) != null ? prices.get(i).replaceAll("[^0-9]", "") : "";
                if (!priceStr.isEmpty()) price = new BigDecimal(priceStr);

                BigDecimal subtotal = qty.multiply(price);
                BigDecimal ppn = BigDecimal.ZERO;
                BigDecimal total = subtotal;

                                if ("EXCLUDE".equals(taxType)) {
                    // DPP = subtotal, PPN = 11% × DPP, Total = DPP + PPN
                    ppn = subtotal.multiply(new BigDecimal("0.11"))
                                 .setScale(2, RoundingMode.HALF_UP);
                    total = subtotal.add(ppn);
                } else if ("INCLUDE".equals(taxType)) {
                    // DPP = subtotal ÷ 1.11, PPN = subtotal - DPP (= subtotal × 11/111)
                    // Kalikan dulu, bagi belakangan, baru dibulatkan
                    ppn = subtotal.multiply(new BigDecimal("11"))
                                 .divide(new BigDecimal("111"), 2, RoundingMode.HALF_UP);
                    total = subtotal;  // Total tetap = subtotal (harga include)
                }

                grandTotal = grandTotal.add(total);
                totalTax = totalTax.add(ppn);

                em.createNativeQuery(
                    "INSERT INTO sal.sales_quotation_dtl " +
                    "(header_id, item_id, uom_id, qty, price, subtotal, tax_amount, total) " +
                    "VALUES (:hdr, :item, :uom, :qty, :price, :subtotal, :tax, :total)")
                    .setParameter("hdr", hdrId).setParameter("item", itemIds.get(i))
                    .setParameter("uom", uomIds.get(i)).setParameter("qty", qty)
                    .setParameter("price", price).setParameter("subtotal", subtotal)
                    .setParameter("tax", ppn).setParameter("total", total).executeUpdate();
            }

            em.createNativeQuery(
                "UPDATE sal.sales_quotation_hdr SET total_amount = :total, tax_amount = :tax WHERE id = :id")
                .setParameter("total", grandTotal).setParameter("tax", totalTax)
                .setParameter("id", hdrId).executeUpdate();

            ra.addFlashAttribute("success", "Quotation " + docNo + " berhasil dibuat.");
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            ra.addFlashAttribute("error", "Gagal simpan: " + root.getMessage());
        }
        return "redirect:/ui/sales/quotations";
    }

    // ============================================================
    // VIEW / DETAIL
    // ============================================================
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Quotation Detail");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("quotationId", id);

        try {
            Object[] hdr = (Object[]) em.createNativeQuery(
                "SELECT q.doc_no, q.doc_date, q.valid_until, q.status, " +
                "COALESCE(c.name, '-'), q.currency, q.remarks, q.total_amount, q.tax_amount, q.tax_type, q.converted_so_id " +
                "FROM sal.sales_quotation_hdr q LEFT JOIN md.customer c ON c.id = q.customer_id " +
                "WHERE q.id = :id AND q.tenant_id = :tenantId AND q.company_id = :compId")
                .setParameter("id", id)
                .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                .getSingleResult();
            
            model.addAttribute("qDocNo", hdr[0]);
            model.addAttribute("qDate", hdr[1]);
            model.addAttribute("qValidUntil", hdr[2]);
            model.addAttribute("qStatus", hdr[3]);
            model.addAttribute("qCustomer", hdr[4]);
            model.addAttribute("qCurrency", hdr[5]);
            model.addAttribute("qRemarks", hdr[6]);
            model.addAttribute("qTotal", hdr[7]);
            model.addAttribute("qTax", hdr[8]);
            model.addAttribute("qTaxType", hdr[9]);
            model.addAttribute("convertedSoId", hdr[10]);

            List<Map<String, Object>> items = new ArrayList<>();
            for (Object[] row : (List<Object[]>) em.createNativeQuery(
                    "SELECT d.id, i.code, i.name, d.qty, u.code, d.price, d.subtotal, d.tax_amount, d.total " +
                    "FROM sal.sales_quotation_dtl d " +
                    "JOIN md.item i ON i.id = d.item_id " +
                    "LEFT JOIN md.uom u ON u.id = d.uom_id " +
                    "WHERE d.header_id = :id ORDER BY d.id").setParameter("id", id).getResultList()) {
                Map<String, Object> m = new HashMap<>();
                m.put("itemCode", row[1]); m.put("itemName", row[2]);
                m.put("qty", row[3]); m.put("uomCode", row[4]);
                m.put("price", row[5]); m.put("subtotal", row[6]);
                m.put("tax", row[7]); m.put("total", row[8]);
                items.add(m);
            }
            model.addAttribute("items", items);
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal memuat: " + e.getMessage());
            return "redirect:/ui/sales/quotations";
        }
        return "ui/sales/quotation_detail";
    }

    // ============================================================
    // ACTION: SEND
    // ============================================================
    @PostMapping("/{id}/send")
    @Transactional
    public String sendQuotation(@PathVariable Long id, RedirectAttributes ra) {
        try {
            int n = em.createNativeQuery(
                "UPDATE sal.sales_quotation_hdr SET status = 'SENT' WHERE id = :id AND status = 'DRAFT' "
                + "AND tenant_id = :tenantId AND company_id = :compId")
                .setParameter("id", id)
                .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                .executeUpdate();
            ra.addFlashAttribute(n > 0 ? "success" : "error",
                n > 0 ? "Quotation ditandai SENT (sudah dikirim ke customer)." : "Hanya DRAFT yang bisa di-send.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/ui/sales/quotations/" + id;
    }

    // ============================================================
    // ACTION: ACCEPT (Customer approve)
    // ============================================================
    @PostMapping("/{id}/accept")
    @Transactional
    public String acceptQuotation(@PathVariable Long id, RedirectAttributes ra) {
        try {
            int n = em.createNativeQuery(
                "UPDATE sal.sales_quotation_hdr SET status = 'ACCEPTED' WHERE id = :id AND status = 'SENT' "
                + "AND tenant_id = :tenantId AND company_id = :compId")
                .setParameter("id", id)
                .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                .executeUpdate();
            ra.addFlashAttribute(n > 0 ? "success" : "error",
                n > 0 ? "Quotation diterima customer. Siap convert ke SO." : "Hanya SENT yang bisa di-accept.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/ui/sales/quotations/" + id;
    }

    // ============================================================
    // ACTION: REJECT
    // ============================================================
    @PostMapping("/{id}/reject")
    @Transactional
    public String rejectQuotation(@PathVariable Long id, RedirectAttributes ra) {
        try {
            int n = em.createNativeQuery(
                "UPDATE sal.sales_quotation_hdr SET status = 'REJECTED' WHERE id = :id AND status = 'SENT' "
                + "AND tenant_id = :tenantId AND company_id = :compId")
                .setParameter("id", id)
                .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                .executeUpdate();
            ra.addFlashAttribute(n > 0 ? "success" : "error",
                n > 0 ? "Quotation ditolak customer." : "Hanya SENT yang bisa di-reject.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/ui/sales/quotations/" + id;
    }

    // ============================================================
    // ACTION: VOID
    // ============================================================
    @PostMapping("/{id}/void")
    @Transactional
    public String voidQuotation(@PathVariable Long id, RedirectAttributes ra) {
        try {
            em.createNativeQuery("UPDATE sal.sales_quotation_hdr SET status = 'VOID' WHERE id = :id "
                + "AND tenant_id = :tenantId AND company_id = :compId")
                .setParameter("id", id)
                .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                .executeUpdate();
            ra.addFlashAttribute("success", "Quotation berhasil di-void.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/ui/sales/quotations/" + id;
    }

    // ============================================================
    // CONVERT TO SO — Buat SO dari Quotation ACCEPTED
    // ============================================================
    @PostMapping("/{id}/convert-to-so")
    @Transactional
    public String convertToSo(@PathVariable Long id, 
                              @RequestParam(required = false) LocalDate soDate,
                              HttpSession session, RedirectAttributes ra) {
        try {
            SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        final Long tenantId = context.tenantId();

            Object[] qhdr = (Object[]) em.createNativeQuery(
                "SELECT doc_no, customer_id, currency, remarks, total_amount, tax_type, tax_amount, status " +
                "FROM sal.sales_quotation_hdr WHERE id = :id AND tenant_id = :tenantId AND company_id = :compId")
                .setParameter("id", id)
                .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                .getSingleResult();
            
            String qNo = qhdr[0].toString();
            Long customerId = ((Number) qhdr[1]).longValue();
            String currency = qhdr[2] != null ? qhdr[2].toString() : "IDR";
            String remarks = qhdr[3] != null ? qhdr[3].toString() : "";
            String taxType = qhdr[5] != null ? qhdr[5].toString() : "EXCLUDE";
            String status = qhdr[7].toString();

            if (!"ACCEPTED".equals(status)) {
                throw new RuntimeException("Hanya Quotation berstatus ACCEPTED yang bisa di-convert. Status: " + status);
            }

            LocalDate finalSoDate = soDate != null ? soDate : LocalDate.now();
            String soNo = numberRangeService.generate("SO", "SO");

            // 1. Insert SO Header
            Long soId = ((Number) em.createNativeQuery(
                "INSERT INTO sal.sales_order_hdr " +
                "(tenant_id, company_id, doc_no, doc_date, customer_id, currency, remarks, status, " +
                " total_amount, tax_type, tax_amount, created_at) " +
                "VALUES (:tenantId, :compId, :docNo, :docDate, :custId, :currency, :remarks, 'DRAFT', 0, :taxType, 0, NOW()) RETURNING id")
                .setParameter("tenantId", tenantId).setParameter("compId", compId)
                .setParameter("docNo", soNo).setParameter("docDate", finalSoDate)
                .setParameter("custId", customerId).setParameter("currency", currency)
                .setParameter("remarks", "Dari Quotation " + qNo + ". " + remarks)
                .setParameter("taxType", taxType)
                .getSingleResult()).longValue();

            // 2. Copy details
            List<Object[]> qDetails = em.createNativeQuery(
                "SELECT item_id, uom_id, qty, price, subtotal, tax_amount, total " +
                "FROM sal.sales_quotation_dtl WHERE header_id = :id").setParameter("id", id).getResultList();

            BigDecimal grandTotal = BigDecimal.ZERO;
            BigDecimal totalTax = BigDecimal.ZERO;

            for (Object[] row : qDetails) {
                Long itemId = ((Number) row[0]).longValue();
                Long uomId = ((Number) row[1]).longValue();
                BigDecimal qty = new BigDecimal(row[2].toString());
                BigDecimal price = new BigDecimal(row[3].toString());
                BigDecimal subtotal = new BigDecimal(row[4].toString());
                BigDecimal tax = new BigDecimal(row[5].toString());
                BigDecimal total = new BigDecimal(row[6].toString());

                grandTotal = grandTotal.add(total);
                totalTax = totalTax.add(tax);

                em.createNativeQuery(
                    "INSERT INTO sal.sales_order_dtl (header_id, item_id, uom_id, qty, price, subtotal) " +
                    "VALUES (:hdr, :item, :uom, :qty, :price, :subtotal)")
                    .setParameter("hdr", soId).setParameter("item", itemId)
                    .setParameter("uom", uomId).setParameter("qty", qty)
                    .setParameter("price", price).setParameter("subtotal", subtotal)
                    .executeUpdate();
            }

            em.createNativeQuery(
                "UPDATE sal.sales_order_hdr SET total_amount = :total, tax_amount = :tax WHERE id = :id")
                .setParameter("total", grandTotal).setParameter("tax", totalTax)
                .setParameter("id", soId).executeUpdate();

            // 3. Update Quotation
            em.createNativeQuery(
                "UPDATE sal.sales_quotation_hdr SET status = 'CONVERTED', converted_so_id = :soId "
                + "WHERE id = :id AND tenant_id = :tenantId AND company_id = :compId")
                .setParameter("soId", soId).setParameter("id", id)
                .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                .executeUpdate();

            ra.addFlashAttribute("success", 
                "Quotation " + qNo + " berhasil di-convert ke SO " + soNo + ". Silakan buka SO list untuk review.");
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            ra.addFlashAttribute("error", "Gagal convert: " + root.getMessage());
        }
        return "redirect:/ui/sales/quotations/" + id;
    }
}