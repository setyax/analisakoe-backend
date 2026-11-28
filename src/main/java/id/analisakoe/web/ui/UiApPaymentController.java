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
@RequestMapping("/ui/finance/ap-payments")
public class UiApPaymentController {

    @Autowired
    private ContextService context;

    private final MenuService menuService;

    @PersistenceContext
    private EntityManager em;

    public UiApPaymentController(MenuService menuService) {
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
        model.addAttribute("pageTitle", "AP Payments");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        List<Map<String, Object>> payments = new ArrayList<>();
        try {
            String sql = "SELECT c.id, c.doc_no, c.doc_date, " +
                         "COALESCE(s.name, '-') AS supplier_name, " +
                         "c.amount, COALESCE(c.remarks, ''), c.status, " +
                         "COALESCE(c.payment_method, 'TRANSFER') AS method, " +
                         "COALESCE(c.ref_no, '-') AS ref_no " +
                         "FROM fin.cash_bank_trx c " +
                         "LEFT JOIN md.supplier s ON s.id = c.supplier_id " +
                         "WHERE c.tenant_id = :tenantId AND c.company_id = :compId AND c.trx_type = 'OUT' " +
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

                m.put("supplierName", row[3]);
                m.put("amount", row[4] != null ? row[4] : BigDecimal.ZERO);
                m.put("remarks", row[5]);
                m.put("status", row[6] != null ? row[6].toString() : "DRAFT");
                m.put("paymentMethod", row[7]);
                m.put("refNo", row[8]);
                payments.add(m);
            }
        } catch (Exception e) {
            model.addAttribute("error", "Gagal memuat data: " + e.getMessage());
        }
        model.addAttribute("payments", payments);
        return "ui/finance/ap_payment_list";
    }

    // ============================================================
    // FORM NEW
    // ============================================================
    @GetMapping("/new")
    @Transactional(readOnly = true)
    public String newForm(@RequestParam(required = false) Long supplierId,
                          HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "New AP Payment");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("selectedSupplierId", supplierId);

        List<Map<String, Object>> suppliers = new ArrayList<>();
        for (Object[] row : (List<Object[]>) em.createNativeQuery(
                "SELECT id, name FROM md.supplier WHERE tenant_id = :tenantId AND company_id = :compId ORDER BY name")
                .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId()).getResultList()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", row[0]);
            m.put("name", row[1]);
            suppliers.add(m);
        }
        model.addAttribute("suppliers", suppliers);

        StringBuilder invSql = new StringBuilder(
            "SELECT pi.id, pi.doc_no, s.name, pi.total_amount, " +
            "COALESCE(pi.paid_amount, 0) AS paid, " +
            "(pi.total_amount - COALESCE(pi.paid_amount, 0)) AS outstanding, " +
            "pi.supplier_id, pi.invoice_date " +
            "FROM pur.purchase_invoice_hdr pi " +
            "JOIN md.supplier s ON s.id = pi.supplier_id " +
            "WHERE pi.tenant_id = :tenantId AND pi.company_id = :compId " +
            "  AND pi.status IN ('POSTED','PARTIAL','CONFIRMED') " +
            "  AND (pi.total_amount - COALESCE(pi.paid_amount, 0)) > 0 ");
        if (supplierId != null) invSql.append(" AND pi.supplier_id = :supId ");
        invSql.append(" ORDER BY pi.id DESC");

        var invQuery = em.createNativeQuery(invSql.toString())
            .setParameter("tenantId", context.tenantId())
            .setParameter("compId", context.companyId());
        if (supplierId != null) invQuery.setParameter("supId", supplierId);

        List<Map<String, Object>> invoices = new ArrayList<>();
        for (Object[] row : (List<Object[]>) invQuery.getResultList()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", row[0]);
            m.put("docNo", row[1]);
            m.put("supplier", row[2]);
            m.put("total", row[3] != null ? row[3] : BigDecimal.ZERO);
            m.put("paid", row[4] != null ? row[4] : BigDecimal.ZERO);
            m.put("outstanding", row[5] != null ? row[5] : BigDecimal.ZERO);
            m.put("supplierId", row[6]);

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

        return "ui/finance/ap_payment_form";
    }

    // ============================================================
    // SAVE (MULTI-INVOICE)
    // ============================================================
    @PostMapping
    @Transactional
    public String save(@RequestParam LocalDate docDate,
                       @RequestParam Long supplierId,
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
            if (invoiceIds == null || invoiceIds.isEmpty()) {
                throw new RuntimeException("Pilih minimal 1 invoice untuk dibayar.");
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
                throw new RuntimeException("Total pembayaran harus lebih dari 0.");
            }

            for (Long invId : invoiceIds) {
                Object[] owner = (Object[]) em.createNativeQuery(
                    "SELECT supplier_id, doc_no FROM pur.purchase_invoice_hdr WHERE id = :id")
                    .setParameter("id", invId).getSingleResult();

                Long invSupplierId = owner[0] != null ? ((Number) owner[0]).longValue() : null;
                String invDocNo = owner[1] != null ? owner[1].toString() : "?";

                if (invSupplierId == null || !invSupplierId.equals(supplierId)) {
                    throw new RuntimeException(
                        "❌ Invoice " + invDocNo + " bukan milik supplier yang dipilih. " +
                        "Pembayaran dibatalkan demi keamanan.");
                }
            }

            Long accBank = null;
            try {
                Object r = em.createNativeQuery("SELECT coa_id FROM fin.bank_account WHERE id = :id")
                    .setParameter("id", bankId).getSingleResult();
                if (r != null) accBank = ((Number) r).longValue();
            } catch (Exception ignore) { }

            if (accBank == null) accBank = getCoaIdByCode("1102");
            Long accHutang = getCoaIdByCode("2101");

            if (accBank == null || accHutang == null) {
                throw new RuntimeException("Akun COA Bank atau Hutang Usaha tidak ditemukan.");
            }

            String docNo = "PAY-" + System.currentTimeMillis();
            Long cashTrxId = ((Number) em.createNativeQuery(
                "INSERT INTO fin.cash_bank_trx " +
                "(tenant_id, company_id, doc_no, doc_date, trx_type, bank_id, amount, remarks, status, " +
                " supplier_id, payment_method, ref_no) " +
                "VALUES (:tenantId, :compId, :docNo, :docDate, 'OUT', :bankId, :amount, :remarks, 'POSTED', " +
                " :supplierId, :method, :refNo) RETURNING id")
                .setParameter("tenantId", tenantId)
                .setParameter("compId", compId)
                .setParameter("docNo", docNo)
                .setParameter("docDate", docDate)
                .setParameter("bankId", bankId)
                .setParameter("amount", totalPay)
                .setParameter("remarks", remarks)
                .setParameter("supplierId", supplierId)
                .setParameter("method", paymentMethod != null ? paymentMethod : "TRANSFER")
                .setParameter("refNo", refNo)
                .getSingleResult()).longValue();

            for (int i = 0; i < invoiceIds.size(); i++) {
                Long invId = invoiceIds.get(i);
                BigDecimal payAmt = payAmounts.get(i);
                if (payAmt == null || payAmt.compareTo(BigDecimal.ZERO) <= 0) continue;

                Object[] inv = (Object[]) em.createNativeQuery(
                    "SELECT total_amount, COALESCE(paid_amount, 0) FROM pur.purchase_invoice_hdr WHERE id = :id")
                    .setParameter("id", invId).getSingleResult();
                BigDecimal total = new BigDecimal(inv[0].toString());
                BigDecimal paid = new BigDecimal(inv[1].toString());
                BigDecimal outstanding = total.subtract(paid);

                if (payAmt.compareTo(outstanding) > 0) {
                    throw new RuntimeException("Pembayaran invoice " + invId + " melebihi outstanding.");
                }

                em.createNativeQuery(
                    "INSERT INTO fin.ap_payment_dtl (payment_id, invoice_id, amount) VALUES (:payId, :invId, :amt)")
                    .setParameter("payId", cashTrxId).setParameter("invId", invId).setParameter("amt", payAmt)
                    .executeUpdate();

                BigDecimal newPaid = paid.add(payAmt);
                String newStatus = newPaid.compareTo(total) >= 0 ? "PAID" : "PARTIAL";
                em.createNativeQuery(
                    "UPDATE pur.purchase_invoice_hdr SET paid_amount = :paid, status = :status WHERE id = :id")
                    .setParameter("paid", newPaid).setParameter("status", newStatus).setParameter("id", invId)
                    .executeUpdate();
            }

            Long jvId = ((Number) em.createNativeQuery(
                "INSERT INTO fin.journal_hdr " +
                "(tenant_id, company_id, doc_no, doc_date, journal_type, remarks, status, ref_type, ref_id) " +
                "VALUES (:tenantId, :compId, 'JV-' || :docNo, :docDate, 'PAYMENT', :remarks, 'POSTED', 'AP_PAYMENT', :cashId) RETURNING id")
                .setParameter("tenantId", tenantId).setParameter("compId", compId)
                .setParameter("docNo", docNo).setParameter("docDate", docDate)
                .setParameter("remarks", "Pembayaran Hutang " + docNo)
                .setParameter("cashId", cashTrxId).getSingleResult()).longValue();

            em.createNativeQuery(
                "INSERT INTO fin.journal_dtl (header_id, account_id, debit, credit, description) " +
                "VALUES (:hdr, :acc, :dr, 0, :desc)")
                .setParameter("hdr", jvId).setParameter("acc", accHutang).setParameter("dr", totalPay)
                .setParameter("desc", "Pelunasan hutang " + docNo).executeUpdate();

            em.createNativeQuery(
                "INSERT INTO fin.journal_dtl (header_id, account_id, debit, credit, description) " +
                "VALUES (:hdr, :acc, 0, :cr, :desc)")
                .setParameter("hdr", jvId).setParameter("acc", accBank).setParameter("cr", totalPay)
                .setParameter("desc", "Pembayaran via bank " + docNo).executeUpdate();

            ra.addFlashAttribute("success", "Pembayaran " + docNo + " berhasil.");
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            ra.addFlashAttribute("error", "Gagal: " + root.getMessage());
        }
        return "redirect:/ui/finance/ap-payments";
    }

    // ============================================================
    // VOID (Reversal) — Handle invoice yang sudah dihapus
    // ============================================================
    @PostMapping("/{id}/void")
    @Transactional
    public String voidPayment(@PathVariable Long id, RedirectAttributes ra) {
        try {
            Object[] trx = (Object[]) em.createNativeQuery(
                "SELECT doc_no, status, tenant_id, company_id FROM fin.cash_bank_trx WHERE id = :id AND trx_type = 'OUT'")
                .setParameter("id", id).getSingleResult();
            String docNo = trx[0].toString();
            String status = trx[1].toString();
            final Long tenantId = ((Number) trx[2]).longValue();
            final Long compId = ((Number) trx[3]).longValue();

            if ("VOID".equals(status)) throw new RuntimeException("Payment sudah VOID.");

            List<Object[]> allocations = em.createNativeQuery(
                "SELECT invoice_id, amount FROM fin.ap_payment_dtl WHERE payment_id = :id")
                .setParameter("id", id).getResultList();

            int reversed = 0;
            int skipped = 0;

            for (Object[] alloc : allocations) {
                Long invId = ((Number) alloc[0]).longValue();
                BigDecimal allocAmt = new BigDecimal(alloc[1].toString());

                List<?> invCheck = em.createNativeQuery(
                    "SELECT total_amount, COALESCE(paid_amount, 0) FROM pur.purchase_invoice_hdr WHERE id = :id")
                    .setParameter("id", invId).getResultList();

                if (invCheck.isEmpty()) {
                    System.out.println("[voidPayment] Invoice ID " + invId + " sudah dihapus, skip reverse");
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
                    "UPDATE pur.purchase_invoice_hdr SET paid_amount = :paid, status = :status WHERE id = :id")
                    .setParameter("paid", newPaid).setParameter("status", newStatus).setParameter("id", invId)
                    .executeUpdate();

                reversed++;
            }

            Object[] jvInfo = null;
            try {
                jvInfo = (Object[]) em.createNativeQuery(
                    "SELECT id FROM fin.journal_hdr WHERE ref_type = 'AP_PAYMENT' AND ref_id = :cashId AND status = 'POSTED'")
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
                    "VALUES (:tenantId, :compId, :docNo, CURRENT_DATE, 'VOID_PAYMENT', :remarks, 'POSTED', 'AP_PAYMENT_VOID', :cashId) RETURNING id")
                    .setParameter("tenantId", tenantId).setParameter("compId", compId)
                    .setParameter("docNo", revDocNo).setParameter("remarks", "Void payment " + docNo)
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
                        .setParameter("desc", "Reversal void payment " + docNo).executeUpdate();
                }
            }

            em.createNativeQuery("UPDATE fin.cash_bank_trx SET status = 'VOID' WHERE id = :id")
                .setParameter("id", id).executeUpdate();

            String msg = "Payment " + docNo + " berhasil di-VOID. " + reversed + " invoice di-reverse.";
            if (skipped > 0) msg += " (" + skipped + " invoice sudah dihapus, di-skip)";
            ra.addFlashAttribute("success", msg);
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            ra.addFlashAttribute("error", "Gagal void: " + root.getMessage());
        }
        return "redirect:/ui/finance/ap-payments";
    }

    // ============================================================
    // DELETE (hanya DRAFT)
    // ============================================================
    @PostMapping("/{id}/delete")
    @Transactional
    public String deletePayment(@PathVariable Long id, RedirectAttributes ra) {
        try {
            Object[] trx = (Object[]) em.createNativeQuery(
                "SELECT status FROM fin.cash_bank_trx WHERE id = :id")
                .setParameter("id", id).getSingleResult();
            String status = trx[0].toString();

            if (!"DRAFT".equals(status)) {
                throw new RuntimeException("Hanya payment DRAFT yang bisa dihapus. Gunakan VOID.");
            }

            em.createNativeQuery("DELETE FROM fin.ap_payment_dtl WHERE payment_id = :id")
                .setParameter("id", id).executeUpdate();
            em.createNativeQuery("DELETE FROM fin.cash_bank_trx WHERE id = :id")
                .setParameter("id", id).executeUpdate();

            ra.addFlashAttribute("success", "Payment berhasil dihapus.");
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            ra.addFlashAttribute("error", "Gagal hapus: " + root.getMessage());
        }
        return "redirect:/ui/finance/ap-payments";
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
        return "redirect:/ui/finance/ap-payments";
    }
}
