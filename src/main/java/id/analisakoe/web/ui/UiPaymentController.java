package id.analisakoe.web.ui;

import org.springframework.beans.factory.annotation.Autowired;

import id.analisakoe.context.ContextService;
import id.analisakoe.web.ui.SessionInterceptor;
import id.analisakoe.web.ui.SessionUser;

import id.analisakoe.core.menu.MenuService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpSession;
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
@RequestMapping("/ui/finance/receipts")
public class UiPaymentController {

    @Autowired
    private ContextService context;

    private final MenuService menuService;

    @PersistenceContext
    private EntityManager em;

    public UiPaymentController(MenuService menuService) {
        this.menuService = menuService;
    }

    // ============================================================
    // LIST
    // ============================================================
    @GetMapping
    @Transactional(readOnly = true)
    public String list(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Customer Receipts");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        List<Map<String, Object>> receipts = new ArrayList<>();
        try {
            String sql = "SELECT c.id, c.doc_no, c.doc_date, " +
                         "COALESCE(cu.name, '-') AS customer_name, " +
                         "c.amount, COALESCE(c.remarks, ''), c.status, " +
                         "COALESCE(c.payment_method, 'TRANSFER') AS method, " +
                         "COALESCE(c.ref_no, '-') AS ref_no " +
                         "FROM fin.cash_bank_trx c " +
                         "LEFT JOIN md.customer cu ON cu.id = c.customer_id " +
                         "WHERE c.tenant_id = :tenantId AND c.company_id = :compId AND c.trx_type = 'IN' " +
                         "ORDER BY c.id DESC";
            List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter("tenantId", context.tenantId())
                .setParameter("compId", context.companyId())
                .getResultList();
            for (Object[] row : rows) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", row[0]);
                m.put("docNo", row[1] != null ? row[1].toString() : "-");

                Object dateObj = row[2];
                LocalDate parsedDate = null;
                if (dateObj instanceof java.sql.Date) {
                    parsedDate = ((java.sql.Date) dateObj).toLocalDate();
                } else if (dateObj instanceof java.sql.Timestamp) {
                    parsedDate = ((java.sql.Timestamp) dateObj).toLocalDateTime().toLocalDate();
                } else if (dateObj instanceof LocalDate) {
                    parsedDate = (LocalDate) dateObj;
                }
                m.put("docDate", parsedDate);

                m.put("customerName", row[3]);
                m.put("amount", row[4] != null ? row[4] : BigDecimal.ZERO);
                m.put("remarks", row[5]);
                m.put("status", row[6] != null ? row[6].toString() : "DRAFT");
                m.put("paymentMethod", row[7]);
                m.put("refNo", row[8]);
                receipts.add(m);
            }
        } catch (Exception e) {
            model.addAttribute("error", "Gagal memuat data: " + e.getMessage());
        }
        model.addAttribute("receipts", receipts);
        return "ui/finance/receipt_list";
    }

    // ============================================================
    // FORM NEW
    // ============================================================
    @GetMapping("/new")
    @Transactional(readOnly = true)
    public String newForm(@RequestParam(required = false) Long customerId,
                          HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "New Customer Receipt");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("selectedCustomerId", customerId);

        // List Customer
        List<Map<String, Object>> customers = new ArrayList<>();
        for (Object[] row : (List<Object[]>) em.createNativeQuery(
                "SELECT id, name FROM md.customer WHERE tenant_id = :tenantId AND company_id = :compId ORDER BY name")
                .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId()).getResultList()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", row[0]);
            m.put("name", row[1]);
            customers.add(m);
        }
        model.addAttribute("customers", customers);

        // Invoice outstanding
        StringBuilder invSql = new StringBuilder(
            "SELECT si.id, si.doc_no, c.name, si.total_amount, " +
            "COALESCE(si.paid_amount, 0) AS paid, " +
            "(si.total_amount - COALESCE(si.paid_amount, 0)) AS outstanding, " +
            "si.customer_id, si.invoice_date " +
            "FROM sal.sales_invoice_hdr si " +
            "JOIN md.customer c ON c.id = si.customer_id " +
            "WHERE si.tenant_id = :tenantId AND si.company_id = :compId " +
            "  AND si.status IN ('POSTED','PARTIAL','CONFIRMED') " +
            "  AND (si.total_amount - COALESCE(si.paid_amount, 0)) > 0 ");
        if (customerId != null) invSql.append(" AND si.customer_id = :custId ");
        invSql.append(" ORDER BY si.id DESC");

        var invQuery = em.createNativeQuery(invSql.toString())
            .setParameter("tenantId", context.tenantId())
            .setParameter("compId", context.companyId());
        if (customerId != null) invQuery.setParameter("custId", customerId);

        List<Map<String, Object>> invoices = new ArrayList<>();
        for (Object[] row : (List<Object[]>) invQuery.getResultList()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", row[0]);
            m.put("docNo", row[1]);
            m.put("customer", row[2]);
            m.put("total", row[3] != null ? row[3] : BigDecimal.ZERO);
            m.put("paid", row[4] != null ? row[4] : BigDecimal.ZERO);
            m.put("outstanding", row[5] != null ? row[5] : BigDecimal.ZERO);
            m.put("customerId", row[6]);

            Object dateObj = row[7];
            LocalDate invDate = null;
            if (dateObj instanceof java.sql.Date) {
                invDate = ((java.sql.Date) dateObj).toLocalDate();
            } else if (dateObj instanceof LocalDate) {
                invDate = (LocalDate) dateObj;
            } else if (dateObj instanceof java.sql.Timestamp) {
                invDate = ((java.sql.Timestamp) dateObj).toLocalDateTime().toLocalDate();
            }
            m.put("invoiceDate", invDate);
            invoices.add(m);
        }
        model.addAttribute("invoices", invoices);

        // Bank (dengan COA)
        List<Map<String, Object>> banks = new ArrayList<>();
        for (Object[] row : (List<Object[]>) em.createNativeQuery(
                "SELECT id, name, COALESCE(coa_id, 0) AS coa_id FROM fin.bank_account ORDER BY name").getResultList()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", row[0]);
            m.put("name", row[1]);
            m.put("coaId", row[2]);
            banks.add(m);
        }
        model.addAttribute("banks", banks);

        return "ui/finance/receipt_form";
    }

    // ============================================================
    // SAVE (MULTI-INVOICE)
    // ============================================================
    @PostMapping
    @Transactional
    public String save(@RequestParam LocalDate docDate,
                       @RequestParam Long customerId,
                       @RequestParam Long bankId,
                       @RequestParam String paymentMethod,
                       @RequestParam(required = false) String refNo,
                       @RequestParam(required = false) String remarks,
                       @RequestParam(value = "invoiceId", required = false) List<Long> invoiceIds,
                       @RequestParam(value = "payAmount", required = false) List<BigDecimal> payAmounts,
                       HttpSession session,
                       RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        final Long tenantId = context.tenantId();
        final Long compId = context.companyId();
        try {
            // 1. Validasi form
            if (invoiceIds == null || invoiceIds.isEmpty()) {
                throw new RuntimeException("Pilih minimal 1 invoice untuk diterima pembayarannya.");
            }
            if (payAmounts == null || invoiceIds.size() != payAmounts.size()) {
                throw new RuntimeException("Jumlah invoice dan nominal tidak cocok.");
            }

            BigDecimal totalPay = BigDecimal.ZERO;
            for (BigDecimal amt : payAmounts) {
                if (amt != null && amt.compareTo(BigDecimal.ZERO) > 0) {
                    totalPay = totalPay.add(amt);
                }
            }
            if (totalPay.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("Total penerimaan harus lebih dari 0.");
            }

            // 2. VALIDASI KRITIS: Semua invoice harus milik customer yang dipilih
            for (Long invId : invoiceIds) {
                Object[] owner = (Object[]) em.createNativeQuery(
                    "SELECT customer_id, doc_no FROM sal.sales_invoice_hdr WHERE id = :id")
                    .setParameter("id", invId).getSingleResult();

                Long invCustomerId = owner[0] != null ? ((Number) owner[0]).longValue() : null;
                String invDocNo = owner[1] != null ? owner[1].toString() : "?";

                if (invCustomerId == null || !invCustomerId.equals(customerId)) {
                    throw new RuntimeException(
                        "❌ Invoice " + invDocNo + " bukan milik customer yang dipilih. " +
                        "Penerimaan dibatalkan demi keamanan.");
                }
            }

            // 3. COA Bank & Piutang
            Long accBank = null;
            try {
                Object r = em.createNativeQuery("SELECT coa_id FROM fin.bank_account WHERE id = :id")
                    .setParameter("id", bankId).getSingleResult();
                if (r != null) accBank = ((Number) r).longValue();
            } catch (Exception ignore) { }

            if (accBank == null) accBank = getCoaIdByCode("1102");
            Long accPiutang = getCoaIdByCode("1201");

            if (accBank == null || accPiutang == null) {
                throw new RuntimeException("Akun COA Bank atau Piutang Usaha tidak ditemukan.");
            }

            // 4. Insert cash_bank_trx (header)
            String docNo = "RCPT-" + System.currentTimeMillis();
            Long cashTrxId = ((Number) em.createNativeQuery(
                "INSERT INTO fin.cash_bank_trx " +
                "(tenant_id, company_id, doc_no, doc_date, trx_type, bank_id, amount, remarks, status, " +
                " customer_id, payment_method, ref_no) " +
                "VALUES (:tenantId, :compId, :docNo, :docDate, 'IN', :bankId, :amount, :remarks, 'POSTED', " +
                " :customerId, :method, :refNo) RETURNING id")
                .setParameter("docNo", docNo)
                .setParameter("docDate", docDate)
                .setParameter("bankId", bankId)
                .setParameter("amount", totalPay)
                .setParameter("remarks", remarks)
                .setParameter("customerId", customerId)
                .setParameter("method", paymentMethod != null ? paymentMethod : "TRANSFER")
                .setParameter("refNo", refNo)
                .setParameter("tenantId", context.tenantId()).getSingleResult()).longValue();

            // 5. Loop invoice: alokasi & update
            for (int i = 0; i < invoiceIds.size(); i++) {
                Long invId = invoiceIds.get(i);
                BigDecimal payAmt = payAmounts.get(i);
                if (payAmt == null || payAmt.compareTo(BigDecimal.ZERO) <= 0) continue;

                Object[] inv = (Object[]) em.createNativeQuery(
                    "SELECT total_amount, COALESCE(paid_amount, 0) FROM sal.sales_invoice_hdr WHERE id = :id")
                    .setParameter("id", invId).getSingleResult();
                BigDecimal total = new BigDecimal(inv[0].toString());
                BigDecimal paid = new BigDecimal(inv[1].toString());
                BigDecimal outstanding = total.subtract(paid);

                if (payAmt.compareTo(outstanding) > 0) {
                    throw new RuntimeException("Penerimaan invoice " + invId + " melebihi outstanding.");
                }

                em.createNativeQuery(
                    "INSERT INTO fin.ar_receipt_dtl (receipt_id, invoice_id, amount) VALUES (:rid, :invId, :amt)")
                    .setParameter("rid", cashTrxId).setParameter("invId", invId).setParameter("amt", payAmt)
                    .executeUpdate();

                BigDecimal newPaid = paid.add(payAmt);
                String newStatus = newPaid.compareTo(total) >= 0 ? "PAID" : "PARTIAL";
                em.createNativeQuery(
                    "UPDATE sal.sales_invoice_hdr SET paid_amount = :paid, status = :status WHERE id = :id")
                    .setParameter("paid", newPaid).setParameter("status", newStatus).setParameter("id", invId)
                    .executeUpdate();
            }

            // 6. Jurnal: Dr Bank, Cr Piutang
            Long jvId = ((Number) em.createNativeQuery(
                "INSERT INTO fin.journal_hdr " +
                "(tenant_id, company_id, doc_no, doc_date, journal_type, remarks, status, ref_type, ref_id) " +
                "VALUES (:tenantId, :compId, 'JV-' || :docNo, :docDate, 'RECEIPT', :remarks, 'POSTED', 'CUSTOMER_RECEIPT', :cashId) RETURNING id")
                .setParameter("tenantId", tenantId).setParameter("compId", compId)
                .setParameter("docNo", docNo).setParameter("docDate", docDate)
                .setParameter("remarks", "Penerimaan dari customer " + docNo)
                .setParameter("cashId", cashTrxId).getSingleResult()).longValue();

            em.createNativeQuery(
                "INSERT INTO fin.journal_dtl (header_id, account_id, debit, credit, description) " +
                "VALUES (:hdr, :acc, :dr, 0, :desc)")
                .setParameter("hdr", jvId).setParameter("acc", accBank).setParameter("dr", totalPay)
                .setParameter("desc", "Penerimaan kas/bank " + docNo).executeUpdate();

            em.createNativeQuery(
                "INSERT INTO fin.journal_dtl (header_id, account_id, debit, credit, description) " +
                "VALUES (:hdr, :acc, 0, :cr, :desc)")
                .setParameter("hdr", jvId).setParameter("acc", accPiutang).setParameter("cr", totalPay)
                .setParameter("desc", "Pelunasan piutang " + docNo).executeUpdate();

            ra.addFlashAttribute("success",
                "Penerimaan " + docNo + " berhasil. " +
                invoiceIds.size() + " invoice diproses. Total: Rp " + totalPay);
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal: " + e.getMessage());
        }
        return "redirect:/ui/finance/receipts";
    }

    // ============================================================
    // VOID (Reversal)
    // ============================================================
        @PostMapping("/{id}/void")
    @Transactional
    public String voidReceipt(@PathVariable Long id, RedirectAttributes ra) {
        try {
            Object[] trx = (Object[]) em.createNativeQuery(
                "SELECT doc_no, status, tenant_id, company_id FROM fin.cash_bank_trx WHERE id = :id AND trx_type = 'IN'")
                .setParameter("id", id).getSingleResult();
            String docNo = trx[0].toString();
            String status = trx[1].toString();
            final Long tenantId = ((Number) trx[2]).longValue();
            final Long compId = ((Number) trx[3]).longValue();

            if ("VOID".equals(status)) throw new RuntimeException("Receipt sudah VOID.");

            // 1. Ambil alokasi
            List<Object[]> allocations = em.createNativeQuery(
                "SELECT invoice_id, amount FROM fin.ar_receipt_dtl WHERE receipt_id = :id")
                .setParameter("id", id).getResultList();

            int reversed = 0;
            int skipped = 0;

            // 2. Reverse paid_amount di setiap invoice (skip jika invoice sudah dihapus)
            for (Object[] alloc : allocations) {
                Long invId = ((Number) alloc[0]).longValue();
                BigDecimal allocAmt = new BigDecimal(alloc[1].toString());

                // Cek dulu apakah invoice masih ada
                List<?> invCheck = em.createNativeQuery(
                    "SELECT total_amount, COALESCE(paid_amount, 0) FROM sal.sales_invoice_hdr WHERE id = :id")
                    .setParameter("id", invId).getResultList();

                if (invCheck.isEmpty()) {
                    // Invoice sudah dihapus — skip reverse, hanya update receipt
                    System.out.println("[voidReceipt] Invoice ID " + invId + " sudah dihapus, skip reverse");
                    skipped++;
                    continue;
                }

                Object[] inv = (Object[]) invCheck.get(0);
                BigDecimal total = new BigDecimal(inv[0].toString());
                BigDecimal paid = new BigDecimal(inv[1].toString());
                BigDecimal newPaid = paid.subtract(allocAmt);
                if (newPaid.compareTo(BigDecimal.ZERO) < 0) newPaid = BigDecimal.ZERO;
                String newStatus = newPaid.compareTo(BigDecimal.ZERO) == 0 ? "POSTED" : "PARTIAL";

                em.createNativeQuery(
                    "UPDATE sal.sales_invoice_hdr SET paid_amount = :paid, status = :status WHERE id = :id")
                    .setParameter("paid", newPaid).setParameter("status", newStatus).setParameter("id", invId)
                    .executeUpdate();

                reversed++;
            }

            // 3. Jurnal reversal (tetap dibuat meskipun invoice dihapus)
            Object[] jvInfo = null;
            try {
                jvInfo = (Object[]) em.createNativeQuery(
                    "SELECT id FROM fin.journal_hdr WHERE ref_type = 'CUSTOMER_RECEIPT' AND ref_id = :cashId")
                    .setParameter("cashId", id).getSingleResult();
            } catch (Exception ignore) { }

            if (jvInfo != null) {
                Long origJvId = ((Number) jvInfo[0]).longValue();
                List<Object[]> jvLines = em.createNativeQuery(
                    "SELECT account_id, debit, credit FROM fin.journal_dtl WHERE header_id = :hdr")
                    .setParameter("hdr", origJvId).getResultList();

                String revDocNo = "REV-" + docNo;
                Long revJvId = ((Number) em.createNativeQuery(
                    "INSERT INTO fin.journal_hdr " +
                    "(tenant_id, company_id, doc_no, doc_date, journal_type, remarks, status, ref_type, ref_id) " +
                    "VALUES (:tenantId, :compId, :docNo, CURRENT_DATE, 'VOID_RECEIPT', :remarks, 'POSTED', 'CUSTOMER_RECEIPT_VOID', :cashId) RETURNING id")
                    .setParameter("tenantId", tenantId).setParameter("compId", compId)
                    .setParameter("docNo", revDocNo).setParameter("remarks", "Void receipt " + docNo)
                    .setParameter("cashId", id).getSingleResult()).longValue();

                for (Object[] line : jvLines) {
                    Long accId = ((Number) line[0]).longValue();
                    BigDecimal dr = new BigDecimal(line[1].toString());
                    BigDecimal cr = new BigDecimal(line[2].toString());
                    em.createNativeQuery(
                        "INSERT INTO fin.journal_dtl (header_id, account_id, debit, credit, description) " +
                        "VALUES (:hdr, :acc, :dr, :cr, :desc)")
                        .setParameter("hdr", revJvId).setParameter("acc", accId)
                        .setParameter("dr", cr).setParameter("cr", dr)
                        .setParameter("desc", "Reversal void receipt " + docNo).executeUpdate();
                }
            }

            // 4. Update status cash_trx
            em.createNativeQuery("UPDATE fin.cash_bank_trx SET status = 'VOID' WHERE id = :id")
                .setParameter("id", id).executeUpdate();

            // 5. Notifikasi
            String msg = "Receipt " + docNo + " berhasil di-VOID. " + reversed + " invoice di-reverse.";
            if (skipped > 0) {
                msg += " (" + skipped + " invoice sudah dihapus, di-skip)";
            }
            ra.addFlashAttribute("success", msg);
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            ra.addFlashAttribute("error", "Gagal void: " + root.getMessage());
        }
        return "redirect:/ui/finance/receipts";
    }

    // ============================================================
    // DELETE (hanya DRAFT)
    // ============================================================
    @PostMapping("/{id}/delete")
    @Transactional
    public String deleteReceipt(@PathVariable Long id, RedirectAttributes ra) {
        try {
            Object[] trx = (Object[]) em.createNativeQuery(
                "SELECT status FROM fin.cash_bank_trx WHERE id = :id AND trx_type = 'IN'")
                .setParameter("id", id).getSingleResult();
            String status = trx[0].toString();

            if (!"DRAFT".equals(status)) {
                throw new RuntimeException("Hanya receipt DRAFT yang bisa dihapus. Gunakan VOID untuk receipt POSTED.");
            }

            em.createNativeQuery("DELETE FROM fin.ar_receipt_dtl WHERE receipt_id = :id")
                .setParameter("id", id).executeUpdate();
            em.createNativeQuery("DELETE FROM fin.cash_bank_trx WHERE id = :id")
                .setParameter("id", id).executeUpdate();

            ra.addFlashAttribute("success", "Receipt berhasil dihapus.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal hapus: " + e.getMessage());
        }
        return "redirect:/ui/finance/receipts";
    }

    private Long getCoaIdByCode(String code) {
        try {
            Object r = em.createNativeQuery("SELECT id FROM fin.chart_of_account WHERE code = :code")
                .setParameter("code", code).getSingleResult();
            return ((Number) r).longValue();
        } catch (Exception e) {
            return null;
        }
    }

    @ExceptionHandler(Exception.class)
    public String handleException(Exception e, RedirectAttributes ra) {
        Throwable rootCause = e;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        ra.addFlashAttribute("error", "Gagal: " + rootCause.getMessage());
        return "redirect:/ui/finance/receipts";
    }
}
