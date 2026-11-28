package id.analisakoe.web.ui;

import id.analisakoe.context.ContextService;

import id.analisakoe.core.menu.MenuService;
import id.analisakoe.fin.service.JournalService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Controller
@RequestMapping("/ui/sales/invoices")
public class UiSalesInvoiceController {

    @Autowired
    private ContextService context;

    // ---- Konstanta alur SO -> DO -> SI ----
    private static final String SO_STATUSES = "('CONFIRMED','DELIVERED')";
    private static final String DO_STATUSES = "('CONFIRMED','DELIVERED','CLOSED')";
    private static final BigDecimal PPN_RATE = new BigDecimal("11");   // sama dengan SO: PPN tetap 11%

    private final MenuService menuService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JournalService journalService;

    @PersistenceContext
    private EntityManager em;

    public UiSalesInvoiceController(MenuService menuService) {
        this.menuService = menuService;
    }

    // ============================================================
    // LIST
    // ============================================================
    @GetMapping
    public String list(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Sales Invoices");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        String sql = "SELECT h.id, h.doc_no, h.invoice_date, COALESCE(c.name, '-'), " +
                     "COALESCE(h.currency, 'IDR'), COALESCE(h.total_amount, 0), " +
                     "COALESCE(h.remarks, ''), h.status, COALESCE(sp.name, '-') " +
                     "FROM sal.sales_invoice_hdr h " +
                     "LEFT JOIN md.customer c ON h.customer_id = c.id " +
                     "LEFT JOIN md.salesperson sp ON h.salesperson_id = sp.id " +
                     "WHERE h.tenant_id = :tenantId AND h.company_id = :compId ORDER BY h.id DESC";

        List<Map<String, Object>> invoices = new ArrayList<>();
        try {
            List<Object[]> results = em.createNativeQuery(sql).setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId()).getResultList();
            for (Object[] row : results) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", row[0]);
                map.put("docNo", row[1] != null ? row[1].toString() : "-");
                Object dateObj = row[2];
                String dateStr = "-";
                if (dateObj != null) {
                    try {
                        java.time.LocalDate ld = null;
                        if (dateObj instanceof java.sql.Date) ld = ((java.sql.Date) dateObj).toLocalDate();
                        else if (dateObj instanceof java.sql.Timestamp) ld = ((java.sql.Timestamp) dateObj).toLocalDateTime().toLocalDate();
                        else if (dateObj instanceof java.time.LocalDate) ld = (java.time.LocalDate) dateObj;
                        else ld = java.time.LocalDate.parse(dateObj.toString());
                        dateStr = ld.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));
                    } catch (Exception e) {
                        dateStr = dateObj.toString();
                    }
                }
                map.put("invoiceDate", dateStr);
                map.put("customerName", row[3]);
                map.put("currency", row[4]);
                map.put("totalAmount", row[5]);
                map.put("remarks", row[6]);
                map.put("status", row[7]);
                map.put("salesperson", row[8]);
                invoices.add(map);
            }
        } catch (Exception e) { model.addAttribute("error", e.getMessage()); }

        model.addAttribute("invoices", invoices);
        return "ui/sales/si_list";
    }

    // ============================================================
    // FORM BARU: daftar DO confirmed + info SO
    // ============================================================
    @GetMapping("/new")
    public String newForm(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId();

        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "New Sales Invoice");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("nextDocNo", generateDocNo(compId));

        String sql =
            "SELECT doh.id, doh.doc_no, so.doc_no, c.name, c.payment_term_days, " +
            "       so.tax_type, COALESCE(so.currency, doh.currency, 'IDR') " +
            "FROM sal.delivery_order_hdr doh " +
            "JOIN sal.sales_order_hdr so ON so.id = doh.so_id " +
            "LEFT JOIN md.customer c ON c.id = COALESCE(doh.customer_id, so.customer_id) " +
            "WHERE doh.tenant_id = :tenantId AND doh.company_id = :compId " +
            "  AND doh.status IN " + DO_STATUSES + " " +
            "  AND so.status  IN " + SO_STATUSES + " " +
            // hanya DO yang masih punya sisa qty untuk ditagih
            "  AND (SELECT COALESCE(SUM(dod.qty), 0) FROM sal.delivery_order_dtl dod " +
            "       WHERE dod.header_id = doh.id) " +
            "    > (SELECT COALESCE(SUM(sid.qty), 0) FROM sal.sales_invoice_dtl sid " +
            "       JOIN sal.sales_invoice_hdr sih ON sih.id = sid.header_id " +
            "       WHERE sih.do_id = doh.id AND sih.status IS DISTINCT FROM 'VOID') " +
            "ORDER BY doh.id DESC";

        List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter("tenantId", context.tenantId()).setParameter("compId", compId)
                .getResultList();

        List<Map<String, Object>> doList = new ArrayList<>();
        for (Object[] r : rows) {
            Map<String, Object> d = new HashMap<>();
            d.put("id", r[0]);
            d.put("docNo", r[1]);
            d.put("soNo", r[2] == null ? "" : r[2].toString());
            d.put("customer", r[3] == null ? "" : r[3].toString());
            d.put("customerTop", r[4]);                                   // null = TOP belum diisi
            d.put("taxType", r[5] == null ? "NO_TAX" : r[5].toString());  // NO_TAX / EXCLUDE / INCLUDE
            d.put("currency", r[6] == null ? "IDR" : r[6].toString().trim());
            doList.add(d);
        }
        model.addAttribute("doList", doList);

        return "ui/sales/si_form";
    }

    // ============================================================
    // API: item DO + sisa qty + harga dari SO
    // ============================================================
    @GetMapping("/api/do-details/{doId}")
    @ResponseBody
    public List<Map<String, Object>> getDoDetails(@PathVariable Long doId, HttpSession session) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId();

        List<Map<String, Object>> result = new ArrayList<>();
        if (!isDoEligible(doId, compId)) return result;

        for (Map<String, Object> line : loadDoLines(doId)) {
            BigDecimal remaining = (BigDecimal) line.get("remainingQty");
            if (remaining.signum() <= 0) continue;     // sudah habis ditagih
            result.add(line);
        }
        return result;
    }

    // ============================================================
    // SIMPAN: semua data diambil dari DO -> SO di server
    // ============================================================
    @Transactional
    @PostMapping
    public String save(@RequestParam LocalDate invDate,
                       @RequestParam Long doId,
                       @RequestParam(required = false) String remarks,
                       @RequestParam("itemIds") List<Long> itemIds,
                       @RequestParam("uomIds") List<Long> uomIds,
                       @RequestParam("qtys") List<BigDecimal> qtys,
                       HttpSession session,
                       RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId();

        try {
            // 1. Validasi DO (confirmed) dan SO asal (confirmed / delivered)
            if (!isDoEligible(doId, compId)) {
                throw new Exception("DO tidak ditemukan, belum CONFIRMED, atau SO asalnya belum CONFIRMED/DELIVERED.");
            }
            if (uomIds.size() != itemIds.size() || qtys.size() != itemIds.size()) {
                throw new Exception("Data item tidak lengkap.");
            }

            // 2. Info SO: customer, tipe pajak, currency, TOP
            Object[] ref = (Object[]) em.createNativeQuery(
                    "SELECT COALESCE(doh.customer_id, so.customer_id), " +
                    "       COALESCE(so.tax_type, 'NO_TAX'), " +
                    "       COALESCE(so.currency, doh.currency, 'IDR'), " +
                    "       c.payment_term_days " +
                    "FROM sal.delivery_order_hdr doh " +
                    "JOIN sal.sales_order_hdr so ON so.id = doh.so_id " +
                    "LEFT JOIN md.customer c ON c.id = COALESCE(doh.customer_id, so.customer_id) " +
                    "WHERE doh.id = :doId")
                    .setParameter("doId", doId).getSingleResult();

            Long customerId = ((Number) ref[0]).longValue();
            String taxType = ref[1].toString();
            String currency = ref[2].toString().trim();
            int topDays = ref[3] == null ? 0 : ((Number) ref[3]).intValue();
            LocalDate dueDate = invDate.plusDays(topDays);

            // 3. Item DO + harga SO + sisa qty (dihitung di server)
            Map<String, Map<String, Object>> doLines = new HashMap<>();
            for (Map<String, Object> l : loadDoLines(doId)) {
                doLines.put(lineKey((Long) l.get("itemId"), (Long) l.get("uomId")), l);
            }

            // 4. Validasi qty + hitung DPP, PPN, total per baris
            List<Object[]> toInsert = new ArrayList<>();   // {itemId, uomId, qty, price, amount, dpp, ppn}
            Map<String, BigDecimal> used = new HashMap<>();
            BigDecimal sumSubtotal = BigDecimal.ZERO;
            BigDecimal sumDpp = BigDecimal.ZERO;
            BigDecimal sumPpn = BigDecimal.ZERO;
            BigDecimal sumTotal = BigDecimal.ZERO;

            for (int i = 0; i < itemIds.size(); i++) {
                Long itemId = itemIds.get(i);
                Long uomId = uomIds.get(i);
                BigDecimal qty = qtys.get(i);
                if (itemId == null || qty == null || qty.signum() <= 0) continue;

                String key = lineKey(itemId, uomId);
                Map<String, Object> line = doLines.get(key);
                if (line == null) {
                    throw new Exception("Item pada baris ke-" + (i + 1) + " tidak ada di DO ini.");
                }

                BigDecimal remaining = (BigDecimal) line.get("remainingQty");
                BigDecimal totalQty = used.getOrDefault(key, BigDecimal.ZERO).add(qty);
                if (totalQty.compareTo(remaining) > 0) {
                    throw new Exception("Qty tagih melebihi sisa DO pada baris ke-" + (i + 1) + ".");
                }
                used.put(key, totalQty);

                BigDecimal price = (BigDecimal) line.get("price");     // harga dari SO
                BigDecimal amount = qty.multiply(price).setScale(2, java.math.RoundingMode.HALF_UP);
                BigDecimal[] t = calcTax(taxType, amount);             // {dpp, ppn, total}

                toInsert.add(new Object[]{itemId, uomId, qty, price, amount, t[0], t[1]});
                sumSubtotal = sumSubtotal.add(amount);
                sumDpp = sumDpp.add(t[0]);
                sumPpn = sumPpn.add(t[1]);
                sumTotal = sumTotal.add(t[2]);
            }

            if (toInsert.isEmpty() || sumSubtotal.signum() <= 0) {
                throw new Exception("Total invoice harus lebih dari 0.");
            }

            // 5. Insert header
            String docNo = generateDocNo(compId);
            Object newIdRaw = em.createNativeQuery(
                    "INSERT INTO sal.sales_invoice_hdr " +
                    "(tenant_id, company_id, doc_no, doc_date, invoice_date, due_date, customer_id, do_id, " +
                    "subtotal, dpp, ppn_rate, tax_amount, total_amount, tax_type, currency, " +
                    "remarks, status, paid_amount, coretax_status) " +
                    "VALUES (:tenantId, :compId, :docNo, :invDate, :invDate, :dueDate, :customerId, :doId, " +
                    ":subtotal, :dpp, :ppnRate, :taxAmount, :totalAmount, :taxType, :currency, " +
                    ":remarks, 'DRAFT', 0, 'NOT_SUBMITTED') RETURNING id")
                    .setParameter("compId", compId)
                    .setParameter("docNo", docNo)
                    .setParameter("invDate", invDate)
                    .setParameter("dueDate", dueDate)
                    .setParameter("customerId", customerId)
                    .setParameter("doId", doId)
                    .setParameter("subtotal", sumSubtotal)
                    .setParameter("dpp", sumDpp)
                    .setParameter("ppnRate", "NO_TAX".equals(taxType) ? BigDecimal.ZERO : PPN_RATE)
                    .setParameter("taxAmount", sumPpn)
                    .setParameter("totalAmount", sumTotal)
                    .setParameter("taxType", taxType)
                    .setParameter("currency", currency)
                    .setParameter("remarks", remarks)
                    .setParameter("tenantId", context.tenantId()).getSingleResult();
            Long invoiceId = ((Number) newIdRaw).longValue();

            // 6. Insert detail
            for (Object[] r : toInsert) {
                em.createNativeQuery(
                        "INSERT INTO sal.sales_invoice_dtl " +
                        "(header_id, item_id, uom_id, qty, price, total, dpp, ppn_amount, tax_type) " +
                        "VALUES (:hdrId, :itemId, :uomId, :qty, :price, :subtotal, :dpp, :ppnAmount, :taxType)")
                        .setParameter("hdrId", invoiceId)
                        .setParameter("itemId", r[0])
                        .setParameter("uomId", r[1])
                        .setParameter("qty", r[2])
                        .setParameter("price", r[3])
                        .setParameter("subtotal", r[4])
                        .setParameter("dpp", r[5])
                        .setParameter("ppnAmount", r[6])
                        .setParameter("taxType", taxType)
                        .executeUpdate();
            }

            ra.addFlashAttribute("success", "Invoice " + docNo + " berhasil. Total: Rp " +
                    String.format("%,.2f", sumTotal));
        } catch (Exception e) {
            e.printStackTrace();
            org.springframework.transaction.interceptor.TransactionAspectSupport
                    .currentTransactionStatus().setRollbackOnly();      // jangan ada header tanpa detail
            ra.addFlashAttribute("error", "Gagal: " + e.getMessage());
        }
        return "redirect:/ui/sales/invoices";
    }

    // ============================================================
    // HELPER ALUR SO -> DO -> SI
    // ============================================================

    /** DO harus CONFIRMED, milik company aktif, dan SO asalnya CONFIRMED / DELIVERED. */
    private boolean isDoEligible(Long doId, Long compId) {
        Number n = (Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM sal.delivery_order_hdr doh " +
                "JOIN sal.sales_order_hdr so ON so.id = doh.so_id " +
                "WHERE doh.id = :doId AND doh.tenant_id = :tenantId AND doh.company_id = :compId " +
                "AND doh.status IN " + DO_STATUSES + " " +
                "AND so.status IN " + SO_STATUSES)
                .setParameter("doId", doId)
                .setParameter("tenantId", context.tenantId()).setParameter("compId", compId)
                .getSingleResult();
        return n.longValue() > 0;
    }

    /** Baris DO + harga dari SO + sisa qty yang belum ditagih (invoice VOID tidak dihitung). */
    private List<Map<String, Object>> loadDoLines(Long doId) {
        String sql =
            "SELECT dod.item_id, dod.uom_id, i.name, u.code, dod.qty, " +
            "       COALESCE((SELECT SUM(sid.qty) FROM sal.sales_invoice_dtl sid " +
            "                 JOIN sal.sales_invoice_hdr sih ON sih.id = sid.header_id " +
            "                 WHERE sih.do_id = dod.header_id " +
            "                   AND sid.item_id = dod.item_id AND sid.uom_id = dod.uom_id " +
            "                   AND sih.status IS DISTINCT FROM 'VOID'), 0), " +
            "       sod.price " +
            "FROM sal.delivery_order_dtl dod " +
            "JOIN sal.delivery_order_hdr doh ON doh.id = dod.header_id " +
            "JOIN sal.sales_order_dtl sod ON sod.header_id = doh.so_id " +
            "     AND sod.item_id = dod.item_id AND sod.uom_id = dod.uom_id " +
            "JOIN md.item i ON i.id = dod.item_id " +
            "JOIN md.uom u ON u.id = dod.uom_id " +
            "WHERE dod.header_id = :doId " +
            "  AND doh.tenant_id = :tenantId AND doh.company_id = :compId " +
            "ORDER BY dod.id";

        List<Map<String, Object>> list = new ArrayList<>();
        List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter("doId", doId)
                .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                .getResultList();
        for (Object[] r : rows) {
            BigDecimal doQty = bd(r[4]);
            Map<String, Object> m = new HashMap<>();
            m.put("itemId", ((Number) r[0]).longValue());
            m.put("uomId", ((Number) r[1]).longValue());
            m.put("itemName", r[2]);
            m.put("uomName", r[3]);
            m.put("doQty", doQty);
            m.put("remainingQty", doQty.subtract(bd(r[5])));
            m.put("price", bd(r[6]));
            list.add(m);
        }
        return list;
    }

    /** Rumus pajak sama dengan Sales Order. Hasil: {dpp, ppn, total}. */
    private BigDecimal[] calcTax(String taxType, BigDecimal amount) {
        BigDecimal hundred = new BigDecimal("100");
        if ("INCLUDE".equals(taxType)) {
            BigDecimal divisor = BigDecimal.ONE.add(PPN_RATE.divide(hundred, 6, java.math.RoundingMode.HALF_UP));
            BigDecimal dpp = amount.divide(divisor, 2, java.math.RoundingMode.HALF_UP);
            return new BigDecimal[]{dpp, amount.subtract(dpp), amount};
        }
        if ("EXCLUDE".equals(taxType)) {
            BigDecimal ppn = amount.multiply(PPN_RATE).divide(hundred, 2, java.math.RoundingMode.HALF_UP);
            return new BigDecimal[]{amount, ppn, amount.add(ppn)};
        }
        return new BigDecimal[]{amount, BigDecimal.ZERO, amount};   // NO_TAX
    }

    private static String lineKey(Long itemId, Long uomId) {
        return itemId + "|" + uomId;
    }

    private static BigDecimal bd(Object o) {
        return o == null ? BigDecimal.ZERO : new BigDecimal(o.toString());
    }

    private String generateDocNo(Long compId) {
        try {
            Object result = em.createNativeQuery(
                "SELECT COUNT(*) FROM sal.sales_invoice_hdr WHERE company_id = :compId")
                .setParameter("compId", compId).getSingleResult();
            Long count = ((Number) result).longValue() + 1;
            return String.format("INV-%d-%04d", LocalDate.now().getYear(), count);
        } catch (Exception e) {
            return "INV-" + System.currentTimeMillis();
        }
    }

    // ============================================================
    // CONFIRM
    // ============================================================
    @PostMapping("/{id}/confirm")
    public String confirm(@PathVariable Long id, RedirectAttributes ra) {
        try {
            transactionTemplate.executeWithoutResult(tx -> {
                // 1. Lock & baca header invoice (jurnal mengikuti company dokumen asal)
                List<?> rows = em.createNativeQuery(
                    "SELECT status, doc_no, tenant_id, company_id, doc_date, COALESCE(total_amount,0), COALESCE(tax_amount,0) " +
                    "FROM sal.sales_invoice_hdr WHERE id = :id AND tenant_id = :tenantId AND company_id = :compId FOR UPDATE")
                    .setParameter("id", id)
                    .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                    .getResultList();
                if (rows.isEmpty()) throw new RuntimeException("Invoice tidak ditemukan.");
                Object[] r = (Object[]) rows.get(0);
                String status = r[0] != null ? r[0].toString() : "";
                if ("CONFIRMED".equals(status) || "POSTED".equals(status)) {
                    throw new RuntimeException("Invoice sudah di-confirm.");
                }
                if (!"DRAFT".equals(status)) {
                    throw new RuntimeException("Hanya Invoice DRAFT yang bisa di-confirm. Status saat ini: " + status);
                }
                String docNo = r[1].toString();
                Long tenantId = ((Number) r[2]).longValue();
                Long compId = ((Number) r[3]).longValue();
                LocalDate docDate = r[4] == null ? LocalDate.now()
                        : (r[4] instanceof java.sql.Date ? ((java.sql.Date) r[4]).toLocalDate()
                           : LocalDate.parse(r[4].toString().substring(0, 10)));
                BigDecimal total = new BigDecimal(r[5].toString());
                BigDecimal tax = new BigDecimal(r[6].toString());

                // 2. Jurnal: Dr Piutang (1201) / Cr Pendapatan (4101) + Cr Hutang PPN (2201)
                String remarks = "Auto-Journal Sales Invoice " + docNo;
                journalService.createJournal(tenantId, compId, docDate, "SALES", remarks, java.util.List.of(
                    new JournalService.JournalLine("1201", total, BigDecimal.ZERO, "Piutang Usaha"),
                    new JournalService.JournalLine("4101", BigDecimal.ZERO, total.subtract(tax), "Pendapatan Penjualan"),
                    new JournalService.JournalLine("2201", BigDecimal.ZERO, tax, "Hutang PPN")
                ));
                // Tandai jurnal agar unconfirm/void bisa menemukannya
                em.createNativeQuery(
                    "UPDATE fin.journal_hdr SET ref_type = 'SALES_INVOICE', ref_id = :id " +
                    "WHERE remarks = :remarks AND journal_type = 'SALES' AND ref_type IS NULL "
                    + "AND tenant_id = :tenantId AND company_id = :compId")
                    .setParameter("id", id).setParameter("remarks", remarks)
                    .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                    .executeUpdate();

                // 3. Status POSTED (dashboard menghitung POSTED)
                em.createNativeQuery(
                    "UPDATE sal.sales_invoice_hdr SET status = 'POSTED' WHERE id = :id "
                    + "AND tenant_id = :tenantId AND company_id = :compId")
                    .setParameter("id", id)
                    .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                    .executeUpdate();
            });
            ra.addFlashAttribute("success", "Sales Invoice berhasil di-post. Jurnal Piutang & Pendapatan dibuat.");
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            ra.addFlashAttribute("error", "Gagal confirm: " + root.getMessage());
        }
        return "redirect:/ui/sales/invoices";
    }

    // ============================================================
    // UNCONFIRM - Kembalikan Invoice ke status DRAFT
    // ============================================================
    @PostMapping("/{id}/unconfirm")
    @Transactional
    public String unconfirmInvoice(@PathVariable Long id, RedirectAttributes ra) {
        try {
            // 1. Cek status invoice
            List<?> statusList = em.createNativeQuery(
                "SELECT status, doc_no FROM sal.sales_invoice_hdr WHERE id = :id "
                + "AND tenant_id = :tenantId AND company_id = :compId")
                .setParameter("id", id)
                .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                .getResultList();

            if (statusList.isEmpty()) {
                throw new RuntimeException("Invoice tidak ditemukan.");
            }

            Object[] row = (Object[]) statusList.get(0);
            String status = row[0] != null ? row[0].toString() : "";
            String docNo = row[1] != null ? row[1].toString() : "?";

            if (!"CONFIRMED".equals(status)) {
                throw new RuntimeException(
                    "Hanya Invoice berstatus CONFIRMED yang bisa di-unconfirm. " +
                    "Status saat ini: " + status);
            }

            // 2. Hapus jurnal SALES yang terkait (dibuat saat confirm)
            journalService.deleteByTypeAndRemarks(context.tenantId(), context.companyId(),
                "SALES", "Auto-Journal Sales Invoice " + docNo);

            // 3. Kembalikan status ke DRAFT
            em.createNativeQuery(
                "UPDATE sal.sales_invoice_hdr SET status = 'DRAFT' WHERE id = :id "
                + "AND tenant_id = :tenantId AND company_id = :compId")
                .setParameter("id", id)
                .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                .executeUpdate();

            ra.addFlashAttribute("success",
                "Sales Invoice " + docNo + " berhasil di-unconfirm. Status kembali ke DRAFT.");

        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            ra.addFlashAttribute("error", "Gagal unconfirm: " + root.getMessage());
        }
        return "redirect:/ui/sales/invoices";
    }

    // ============================================================
    // VOID
    // ============================================================
    @PostMapping("/{id}/void")
    @Transactional
    public String voidInvoice(@PathVariable Long id, RedirectAttributes ra) {
        try {
            // 1. Cek status invoice
            List<?> statusList = em.createNativeQuery(
                "SELECT status, paid_amount, doc_no, tenant_id, company_id FROM sal.sales_invoice_hdr "
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
                    "Invoice " + docNo + " sudah LUNAS (PAID). Tidak bisa di-void. " +
                    "Void receipt pembayaran terlebih dahulu jika perlu.");
            }
            if ("PARTIAL".equals(status)) {
                throw new RuntimeException(
                    "Invoice " + docNo + " sudah dibayar sebagian (PARTIAL: Rp " + paid + "). " +
                    "Void receipt pembayaran terlebih dahulu.");
            }
            if ("VOID".equals(status)) {
                throw new RuntimeException("Invoice " + docNo + " sudah VOID.");
            }

            // 3. Cek juga paid_amount > 0 (double protection)
            if (paid.compareTo(BigDecimal.ZERO) > 0) {
                throw new RuntimeException(
                    "Invoice " + docNo + " memiliki pembayaran (Rp " + paid + "). " +
                    "Tidak bisa di-void sebelum payment di-void.");
            }

            // 4. Cek apakah sudah ada payment aktif (defensive check)
            Long activePayments = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM fin.ar_receipt_dtl rd " +
                "JOIN fin.cash_bank_trx cbtr ON cbtr.id = rd.receipt_id " +
                "WHERE rd.invoice_id = :id AND UPPER(TRIM(cbtr.status)) <> 'VOID'")
                .setParameter("id", id).getSingleResult()).longValue();

            if (activePayments > 0) {
                throw new RuntimeException(
                    "Invoice " + docNo + " punya " + activePayments + " receipt aktif. " +
                    "Void receipt terlebih dahulu sebelum void invoice.");
            }

            // 5. Cek apakah SI sudah dibuatkan Delivery Return atau transaksi lain (opsional)
            // ...

            // 6. Lakukan void + reversal jurnal
            em.createNativeQuery(
                "UPDATE sal.sales_invoice_hdr SET status = 'VOID' WHERE id = :id "
                + "AND tenant_id = :tenantId AND company_id = :compId")
                .setParameter("id", id)
                .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                .executeUpdate();

            // 7. Reverse jurnal SALES (jika ada)
            try {
                Object[] jvInfo = (Object[]) em.createNativeQuery(
                    "SELECT id FROM fin.journal_hdr WHERE ref_type = 'SALES_INVOICE' AND ref_id = :id AND status = 'POSTED' "
                    + "AND tenant_id = :tenantId AND company_id = :compId")
                    .setParameter("id", id)
                    .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                    .getSingleResult();

                Long origJvId = ((Number) jvInfo[0]).longValue();
                List<Object[]> jvLines = em.createNativeQuery(
                    "SELECT account_id, debit, credit FROM fin.journal_dtl WHERE header_id = :hdr")
                    .setParameter("hdr", origJvId).getResultList();

                String revDocNo = "REV-VOID-SI-" + id;
                Long revJvId = ((Number) em.createNativeQuery(
                    "INSERT INTO fin.journal_hdr " +
                    "(tenant_id, company_id, doc_no, doc_date, journal_type, remarks, status, ref_type, ref_id) " +
                    "VALUES (:tenantId, :compId, :docNo, CURRENT_DATE, 'VOID_SALES', :remarks, 'POSTED', 'SALES_INVOICE_VOID', :id) RETURNING id")
                    .setParameter("tenantId", tenantId).setParameter("compId", compId)
                    .setParameter("docNo", revDocNo)
                    .setParameter("remarks", "Void journal for SI " + docNo)
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
                        .setParameter("desc", "Reversal void SI " + docNo).executeUpdate();
                }
            } catch (Exception ignore) { /* Jika tidak ada jurnal, skip */ }

            ra.addFlashAttribute("success", "Invoice " + docNo + " berhasil di-VOID.");
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            ra.addFlashAttribute("error", root.getMessage());
        }
        return "redirect:/ui/sales/invoices";
    }

    // ============================================
    // EDIT FORM - Load header + detail existing
    // ============================================
    @GetMapping("/{id}/edit")
    @Transactional(readOnly = true)
    public String editForm(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Edit Sales Invoice");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        try {
            // Load header
            List<?> hdrRows = em.createNativeQuery(
                "SELECT id, doc_no, invoice_date, due_date, customer_id, total_amount, tax_amount, " +
                "remarks, status, currency, paid_amount " +
                "FROM sal.sales_invoice_hdr WHERE id = :id AND tenant_id = :tenantId AND company_id = :compId")
                .setParameter("id", id)
                .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                .getResultList();
            if (hdrRows.isEmpty()) {
                ra.addFlashAttribute("error", "Invoice #" + id + " tidak ditemukan.");
                return "redirect:/ui/sales/invoices";
            }
            Object[] hdr = (Object[]) hdrRows.get(0);

            // Cek status: hanya boleh edit DRAFT / CONFIRMED
            String status = hdr[8] != null ? hdr[8].toString() : "";
            if ("POSTED".equals(status) || "PAID".equals(status) || "VOID".equals(status)) {
                ra.addFlashAttribute("error", "Invoice status " + status + " tidak bisa diedit. Unpost dulu.");
                return "redirect:/ui/sales/invoices";
            }

            model.addAttribute("invId", hdr[0]);
            model.addAttribute("docNo", hdr[1] != null ? hdr[1].toString() : "-");
            model.addAttribute("invoiceDate", hdr[2] != null ? hdr[2].toString() : "-");
            model.addAttribute("dueDate", hdr[3] != null ? hdr[3].toString() : "-");
            model.addAttribute("customerId", hdr[4]);
            model.addAttribute("totalAmount", hdr[5]);
            model.addAttribute("taxAmount", hdr[6] != null ? hdr[6] : BigDecimal.ZERO);
            model.addAttribute("remarks", hdr[7] != null ? hdr[7].toString() : "");
            model.addAttribute("status", status);
            model.addAttribute("currency", hdr[9] != null ? hdr[9].toString() : "IDR");
            model.addAttribute("paidAmount", hdr[10] != null ? hdr[10] : BigDecimal.ZERO);

            // Load customer
            try {
                Object[] cust = (Object[]) em.createNativeQuery(
                    "SELECT name FROM md.customer WHERE id = :id AND tenant_id = :tenantId AND company_id = :compId")
                    .setParameter("id", hdr[4])
                    .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                    .getSingleResult();
                model.addAttribute("customerName", cust[0]);
            } catch (Exception e) { model.addAttribute("customerName", "-"); }

            // Load detail items
            List<?> dtlRows = em.createNativeQuery(
                "SELECT d.id, i.code, i.name, d.qty, d.price, d.total " +
                "FROM sal.sales_invoice_dtl d JOIN md.item i ON i.id = d.item_id " +
                "WHERE d.header_id = :id ORDER BY d.id")
                .setParameter("id", id).getResultList();

            List<Map<String, Object>> items = new ArrayList<>();
            for (Object r : dtlRows) {
                Object[] rr = (Object[]) r;
                Map<String, Object> m = new HashMap<>();
                m.put("id", rr[0]);
                m.put("code", rr[1] != null ? rr[1].toString() : "-");
                m.put("name", rr[2] != null ? rr[2].toString() : "-");
                m.put("qty", rr[3]);
                m.put("price", rr[4]);
                m.put("total", rr[5]);
                items.add(m);
            }
            model.addAttribute("items", items);

        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal load invoice: " + e.getMessage());
            return "redirect:/ui/sales/invoices";
        }

        return "ui/sales/si_edit_form";
    }

    // ============================================
    // UPDATE - Hanya due_date & remarks yang bisa diubah
    // ============================================
    @PostMapping("/{id}")
    @Transactional
    public String update(@PathVariable Long id,
                         @RequestParam String dueDate,
                         @RequestParam(required = false) String remarks,
                         RedirectAttributes ra) {
        try {
            // Cek status
            List<?> statusRows = em.createNativeQuery(
                "SELECT status FROM sal.sales_invoice_hdr WHERE id = :id "
                + "AND tenant_id = :tenantId AND company_id = :compId")
                .setParameter("id", id)
                .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                .getResultList();
            if (statusRows.isEmpty()) throw new RuntimeException("Invoice tidak ditemukan.");
            String status = statusRows.get(0).toString();
            if ("POSTED".equals(status) || "PAID".equals(status) || "VOID".equals(status)) {
                throw new RuntimeException("Invoice status " + status + " tidak bisa diedit.");
            }

            em.createNativeQuery(
                "UPDATE sal.sales_invoice_hdr SET due_date = :due, remarks = :rem WHERE id = :id "
                + "AND tenant_id = :tenantId AND company_id = :compId")
                .setParameter("due", LocalDate.parse(dueDate))
                .setParameter("rem", remarks)
                .setParameter("id", id)
                .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                .executeUpdate();

            ra.addFlashAttribute("success", "Sales Invoice #" + id + " berhasil diupdate.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal update: " + e.getMessage());
            return "redirect:/ui/sales/invoices/" + id + "/edit";
        }
        return "redirect:/ui/sales/invoices";
    }
}