package id.analisakoe.web.ui;
import id.analisakoe.web.ui.SessionInterceptor;
import id.analisakoe.web.ui.SessionUser;

import id.analisakoe.core.menu.MenuService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.support.TransactionTemplate;
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
@RequestMapping("/ui/finance/expenses")
public class UiExpenseController {

    private final MenuService menuService;

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private TransactionTemplate transactionTemplate;

    public UiExpenseController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping
    public String list(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Biaya Operasional");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        List<Map<String, Object>> expenses = new ArrayList<>();
        try {
            // Hanya expense milik cabang aktif
            String sql = "SELECT c.id, c.doc_no, c.doc_date, c.amount, COALESCE(c.remarks,''), COALESCE(c.status,'-') " +
                         "FROM fin.cash_bank_trx c WHERE c.trx_type = 'OUT' AND c.remarks LIKE 'EXPENSE:%' " +
                         "AND c.company_id = :compId " +
                         "ORDER BY c.id DESC";
            List<Object[]> rows = em.createNativeQuery(sql)
                    .setParameter("compId", compId).getResultList();
            for (Object[] row : rows) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", row[0]);
                m.put("docNo", row[1]);
                Object dateObj = row[2];
                String dateStr = "-";
                if (dateObj != null) {
                    try {
                        java.time.LocalDate ld = null;
                        if (dateObj instanceof java.sql.Date) ld = ((java.sql.Date) dateObj).toLocalDate();
                        else if (dateObj instanceof java.sql.Timestamp) ld = ((java.sql.Timestamp) dateObj).toLocalDateTime().toLocalDate();
                        else ld = java.time.LocalDate.parse(dateObj.toString());
                        dateStr = ld.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));
                    } catch (Exception e) { dateStr = dateObj.toString(); }
                }
                m.put("docDate", dateStr);
                m.put("amount", row[3]);
                m.put("remarks", ((String) row[4]).replaceFirst("EXPENSE:", "").trim());
                m.put("status", row[5]);
                expenses.add(m);
            }
        } catch (Exception e) { model.addAttribute("error", "Gagal memuat: " + e.getMessage()); }
        model.addAttribute("expenses", expenses);
        return "ui/finance/expense_list";
    }

    @GetMapping("/new")
    public String newForm(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "New Expense");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("today", LocalDate.now());

        List<Map<String, Object>> accounts = new ArrayList<>();
        for (Object[] row : (List<Object[]>) em.createNativeQuery(
            "SELECT id, code, name FROM fin.chart_of_account WHERE code LIKE '6%' AND account_type = 'EXPENSE' ORDER BY code").getResultList()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", row[0]); m.put("code", row[1]); m.put("name", row[2]);
            accounts.add(m);
        }
        model.addAttribute("accounts", accounts);

        List<Map<String, Object>> banks = new ArrayList<>();
        for (Object[] row : (List<Object[]>) em.createNativeQuery("SELECT id, name FROM fin.bank_account ORDER BY name").getResultList()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", row[0]); m.put("name", row[1]);
            banks.add(m);
        }
        model.addAttribute("banks", banks);
        return "ui/finance/expense_form";
    }

    @PostMapping
    public String save(@RequestParam LocalDate docDate,
                       @RequestParam Long expenseAccountId,
                       @RequestParam Long bankId,
                       @RequestParam BigDecimal amount,
                       @RequestParam String description,
                       HttpSession session,
                       RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        final Long tenantId = (user != null && user.getTenantId() != null) ? user.getTenantId() : 1L;
        final Long compId = (user != null && user.getActiveCompanyId() != null) ? user.getActiveCompanyId() : 11L;
        try {
            transactionTemplate.executeWithoutResult(tx -> {
                if (amount.compareTo(BigDecimal.ZERO) <= 0)
                    throw new IllegalArgumentException("Nominal harus lebih dari 0.");

                Long accBankId = ((Number) em.createNativeQuery("SELECT id FROM fin.chart_of_account WHERE code = '1102'").getSingleResult()).longValue();

                String docNo = "EXP-" + System.currentTimeMillis();
                String remarksDb = "EXPENSE: " + description;

                Long cashTrxId = ((Number) em.createNativeQuery(
                    "INSERT INTO fin.cash_bank_trx (tenant_id, company_id, doc_no, doc_date, trx_type, bank_id, amount, remarks, status) " +
                    "VALUES (:tenantId, :compId, :docNo, :docDate, 'OUT', :bankId, :amount, :remarks, 'POSTED') RETURNING id")
                    .setParameter("tenantId", tenantId).setParameter("compId", compId)
                    .setParameter("docNo", docNo).setParameter("docDate", docDate)
                    .setParameter("bankId", bankId).setParameter("amount", amount)
                    .setParameter("remarks", remarksDb).getSingleResult()).longValue();

                Long hdrId = ((Number) em.createNativeQuery(
                    "INSERT INTO fin.journal_hdr (tenant_id, company_id, doc_no, doc_date, journal_type, remarks, status, ref_type, ref_id) " +
                    "VALUES (:tenantId, :compId, 'JV-' || :docNo, :docDate, 'EXPENSE', :remarks, 'POSTED', 'EXPENSE', :cashId) RETURNING id")
                    .setParameter("tenantId", tenantId).setParameter("compId", compId)
                    .setParameter("docNo", docNo).setParameter("docDate", docDate)
                    .setParameter("remarks", description).setParameter("cashId", cashTrxId).getSingleResult()).longValue();

                em.createNativeQuery("INSERT INTO fin.journal_dtl (header_id, account_id, debit, credit, description) VALUES (:hdr, :acc, :dr, 0, :desc)")
                    .setParameter("hdr", hdrId).setParameter("acc", expenseAccountId).setParameter("dr", amount).setParameter("desc", description).executeUpdate();

                em.createNativeQuery("INSERT INTO fin.journal_dtl (header_id, account_id, debit, credit, description) VALUES (:hdr, :acc, 0, :cr, :desc)")
                    .setParameter("hdr", hdrId).setParameter("acc", accBankId).setParameter("cr", amount).setParameter("desc", description).executeUpdate();

                ra.addFlashAttribute("success", "Pengeluaran " + docNo + " berhasil dicatat.");
            });
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal: " + rootMessage(e));
        }
        return "redirect:/ui/finance/expenses";
    }

    // ==================== VOID (Reversal jurnal) ====================
    @PostMapping("/{id}/void")
    public String voidExpense(@PathVariable Long id, RedirectAttributes ra) {
        try {
            transactionTemplate.executeWithoutResult(tx -> {
                Object[] trx = (Object[]) em.createNativeQuery(
                        "SELECT doc_no, status, amount, remarks, tenant_id, company_id FROM fin.cash_bank_trx " +
                        "WHERE id = :id AND trx_type = 'OUT'")
                        .setParameter("id", id).getSingleResult();
                String docNo = trx[0].toString();
                String status = trx[1].toString();
                BigDecimal amount = new BigDecimal(trx[2].toString());
                String remarks = trx[3] != null ? trx[3].toString() : "";
                Long tenantId = ((Number) trx[4]).longValue();
                Long compId = ((Number) trx[5]).longValue();

                if ("VOID".equalsIgnoreCase(status))
                    throw new IllegalStateException("Expense " + docNo + " sudah VOID.");

                // Jurnal asli expense
                Long origJvId = null;
                try {
                    origJvId = ((Number) em.createNativeQuery(
                            "SELECT id FROM fin.journal_hdr WHERE ref_type = 'EXPENSE' AND ref_id = :id AND status = 'POSTED'")
                            .setParameter("id", id).getSingleResult()).longValue();
                } catch (Exception ignore) { }

                // Jurnal pembalik (mengikuti company & tenant dokumen asal)
                String desc = remarks.replaceFirst("^EXPENSE:\\s*", "");
                Long revJvId = ((Number) em.createNativeQuery(
                        "INSERT INTO fin.journal_hdr (tenant_id, company_id, doc_no, doc_date, journal_type, remarks, status, ref_type, ref_id) " +
                        "VALUES (:tenantId, :compId, :docNo, CURRENT_DATE, 'VOID_EXPENSE', :remarks, 'POSTED', 'EXPENSE_VOID', :cashId) RETURNING id")
                        .setParameter("tenantId", tenantId).setParameter("compId", compId)
                        .setParameter("docNo", "REV-VOID-EXP-" + id)
                        .setParameter("remarks", "Void expense " + docNo)
                        .setParameter("cashId", id).getSingleResult()).longValue();

                if (origJvId != null) {
                    List<Object[]> lines = em.createNativeQuery(
                            "SELECT account_id, debit, credit, COALESCE(description,'') FROM fin.journal_dtl WHERE header_id = :hdr")
                            .setParameter("hdr", origJvId).getResultList();
                    for (Object[] line : lines) {
                        Long accId = ((Number) line[0]).longValue();
                        BigDecimal dr = line[1] != null ? new BigDecimal(line[1].toString()) : BigDecimal.ZERO;
                        BigDecimal cr = line[2] != null ? new BigDecimal(line[2].toString()) : BigDecimal.ZERO;
                        em.createNativeQuery(
                                "INSERT INTO fin.journal_dtl (header_id, account_id, debit, credit, description) " +
                                "VALUES (:hdr, :acc, :dr, :cr, :desc)")
                            .setParameter("hdr", revJvId).setParameter("acc", accId)
                            .setParameter("dr", cr).setParameter("cr", dr)
                            .setParameter("desc", "Void: " + desc).executeUpdate();
                    }
                } else {
                    // Tanpa jurnal asli (kasus lama): balikkan bank (debit) saja agar saldo bank kembali
                    em.createNativeQuery(
                            "INSERT INTO fin.journal_dtl (header_id, account_id, debit, credit, description) " +
                            "SELECT :hdr, bank.coa_id, :amount, 0, 'Void: ' FROM fin.bank_account bank WHERE bank.id = " +
                            "(SELECT bank_id FROM fin.cash_bank_trx WHERE id = :id)")
                        .setParameter("hdr", revJvId).setParameter("amount", amount).setParameter("id", id).executeUpdate();
                }

                em.createNativeQuery("UPDATE fin.cash_bank_trx SET status = 'VOID' WHERE id = :id")
                    .setParameter("id", id).executeUpdate();
            });
            ra.addFlashAttribute("success", "Expense berhasil di-VOID (jurnal dibalik).");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal void: " + rootMessage(e));
        }
        return "redirect:/ui/finance/expenses";
    }

    // ==================== DELETE (hanya yang sudah VOID) ====================
    @PostMapping("/{id}/delete")
    public String deleteExpense(@PathVariable Long id, RedirectAttributes ra) {
        try {
            transactionTemplate.executeWithoutResult(tx -> {
                Object[] trx = (Object[]) em.createNativeQuery(
                        "SELECT doc_no, status FROM fin.cash_bank_trx WHERE id = :id AND trx_type = 'OUT'")
                        .setParameter("id", id).getSingleResult();
                String docNo = trx[0].toString();
                String status = trx[1].toString();

                if (!"VOID".equalsIgnoreCase(status))
                    throw new IllegalStateException(
                        "Expense " + docNo + " masih POSTED. Void dulu sebelum menghapus.");

                // Hapus jurnal pembalik dan jurnal asli yang terhubung ke trx ini
                List<Integer> hdrIds = em.createNativeQuery(
                        "SELECT id FROM fin.journal_hdr WHERE (ref_type IN ('EXPENSE','EXPENSE_VOID') AND ref_id = :id)")
                        .setParameter("id", id).getResultList();
                for (Object hdrId : hdrIds) {
                    em.createNativeQuery("DELETE FROM fin.journal_dtl WHERE header_id = :hdr")
                        .setParameter("hdr", ((Number) hdrId).longValue()).executeUpdate();
                    em.createNativeQuery("DELETE FROM fin.journal_hdr WHERE id = :hdr")
                        .setParameter("hdr", ((Number) hdrId).longValue()).executeUpdate();
                }

                em.createNativeQuery("DELETE FROM fin.cash_bank_trx WHERE id = :id")
                    .setParameter("id", id).executeUpdate();
            });
            ra.addFlashAttribute("success", "Expense berhasil dihapus.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal hapus: " + rootMessage(e));
        }
        return "redirect:/ui/finance/expenses";
    }

    private String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
        return cur.getMessage() != null ? cur.getMessage() : cur.toString();
    }
}
