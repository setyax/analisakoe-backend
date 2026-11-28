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
@RequestMapping("/ui/finance/journals")
public class UiJournalController {

    @Autowired
    private ContextService context;

    private final MenuService menuService;

    @PersistenceContext
    private EntityManager em;

    public UiJournalController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping
    public String list(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "General Journals");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        List<Map<String, Object>> journals = new ArrayList<>();
        try {
            String sql = "SELECT id, doc_no, doc_date, journal_type, COALESCE(remarks, ''), COALESCE(status, 'UNKNOWN') " +
                         "FROM fin.journal_hdr WHERE tenant_id = :tenantId AND company_id = :compId ORDER BY id DESC";
            List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter("tenantId", context.tenantId())
                .setParameter("compId", context.companyId())
                .getResultList();
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
                        else if (dateObj instanceof java.time.LocalDate) ld = (java.time.LocalDate) dateObj;
                        else ld = java.time.LocalDate.parse(dateObj.toString());
                        dateStr = ld.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));
                    } catch (Exception e) {
                        dateStr = dateObj.toString();
                    }
                }
                m.put("docDate", dateStr);
                
                m.put("journalType", row[3]);
                m.put("remarks", row[4]);
                m.put("status", row[5]);
                journals.add(m);
            }
        } catch (Exception e) {
            model.addAttribute("error", "Gagal memuat data: " + e.getMessage());
        }
        model.addAttribute("journals", journals);
        return "ui/finance/journal_list";
    }

    // ============================================================
    // VIEW — detail jurnal (header + lines)
    // ============================================================
    @GetMapping("/{id}/view")
    public String view(@PathVariable Long id, HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Journal Detail");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));

        List<?> hdrRows = em.createNativeQuery(
            "SELECT h.id, h.doc_no, h.doc_date, h.journal_type, COALESCE(h.remarks, ''), COALESCE(h.status, 'UNKNOWN'), " +
            "COALESCE(c.name, '-') " +
            "FROM fin.journal_hdr h " +
            "LEFT JOIN core.company c ON c.id = h.company_id " +
            "WHERE h.id = :id")
            .setParameter("id", id).getResultList();
        if (hdrRows.isEmpty()) {
            model.addAttribute("error", "Jurnal tidak ditemukan.");
            return "ui/finance/journal_view";
        }
        Object[] h = (Object[]) hdrRows.get(0);
        model.addAttribute("id", h[0]);
        model.addAttribute("docNo", h[1]);
        Object dateObj = h[2];
        String dateStr = "-";
        if (dateObj != null) {
            try {
                java.time.LocalDate ld = null;
                if (dateObj instanceof java.sql.Date) ld = ((java.sql.Date) dateObj).toLocalDate();
                else if (dateObj instanceof java.sql.Timestamp) ld = ((java.sql.Timestamp) dateObj).toLocalDateTime().toLocalDate();
                else if (dateObj instanceof java.time.LocalDate) ld = (java.time.LocalDate) dateObj;
                else ld = java.time.LocalDate.parse(dateObj.toString().substring(0, 10));
                dateStr = ld.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));
            } catch (Exception ex) { dateStr = dateObj.toString(); }
        }
        model.addAttribute("docDate", dateStr);
        model.addAttribute("journalType", h[3]);
        model.addAttribute("remarks", h[4]);
        model.addAttribute("status", h[5]);
        model.addAttribute("companyName", h[6]);

        BigDecimal totalDebit = BigDecimal.ZERO, totalCredit = BigDecimal.ZERO;
        List<Map<String, Object>> lines = new ArrayList<>();
        List<?> lineRows = em.createNativeQuery(
            "SELECT d.id, c.code, c.name, COALESCE(d.description, ''), COALESCE(d.debit, 0), COALESCE(d.credit, 0) " +
            "FROM fin.journal_dtl d " +
            "LEFT JOIN fin.chart_of_account c ON c.id = d.account_id " +
            "WHERE d.header_id = :id ORDER BY d.id")
            .setParameter("id", id).getResultList();
        int no = 1;
        for (Object rowObj : lineRows) {
            Object[] r = (Object[]) rowObj;
            Map<String, Object> m = new HashMap<>();
            m.put("no", no++);
            m.put("coaCode", r[1] != null ? r[1].toString() : "-");
            m.put("coaName", r[2] != null ? r[2].toString() : "-");
            m.put("description", r[3] != null ? r[3].toString() : "-");
            BigDecimal debit = new BigDecimal(r[4].toString());
            BigDecimal credit = new BigDecimal(r[5].toString());
            java.text.DecimalFormat nf = new java.text.DecimalFormat("#,##0.00");
            java.text.DecimalFormatSymbols sym = nf.getDecimalFormatSymbols();
            sym.setDecimalSeparator(','); sym.setGroupingSeparator('.');
            nf.setDecimalFormatSymbols(sym);
            m.put("debitStr", debit.compareTo(BigDecimal.ZERO) != 0 ? nf.format(debit) : "-");
            m.put("creditStr", credit.compareTo(BigDecimal.ZERO) != 0 ? nf.format(credit) : "-");
            m.put("debit", debit);
            m.put("credit", credit);
            totalDebit = totalDebit.add(debit);
            totalCredit = totalCredit.add(credit);
            lines.add(m);
        }
        model.addAttribute("lines", lines);
        java.text.DecimalFormat nfT = new java.text.DecimalFormat("#,##0.00");
        java.text.DecimalFormatSymbols symT = nfT.getDecimalFormatSymbols();
        symT.setDecimalSeparator(','); symT.setGroupingSeparator('.');
        nfT.setDecimalFormatSymbols(symT);
        model.addAttribute("totalDebitStr", nfT.format(totalDebit));
        model.addAttribute("totalCreditStr", nfT.format(totalCredit));
        return "ui/finance/journal_view";
    }

    @GetMapping("/new")
    public String newForm(HttpSession session, Model model) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "New General Journal");
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
        model.addAttribute("today", LocalDate.now());
        
        List<Map<String,Object>> accounts = new ArrayList<>();
        for (Object[] row : (List<Object[]>) em.createNativeQuery(
                "SELECT id, code, name FROM fin.chart_of_account " +
                "WHERE tenant_id = :tenantId AND company_id = :compId ORDER BY code")
                .setParameter("tenantId", context.tenantId())
                .setParameter("compId", context.companyId())
                .getResultList()) {
            Map<String,Object> m = new HashMap<>();
            m.put("id", row[0]); m.put("code", row[1]); m.put("name", row[2]);
            accounts.add(m);
        }
        model.addAttribute("accounts", accounts);
        return "ui/finance/journal_form";
    }

    @PostMapping("/save")
    @Transactional
    public String saveJournal(@RequestParam LocalDate docDate,
                              @RequestParam String remarks,
                              @RequestParam Long debitAccountId,
                              @RequestParam Long creditAccountId,
                              @RequestParam BigDecimal amount,
                              HttpSession session,
                              RedirectAttributes ra) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        final Long tenantId = context.tenantId();
        final Long compId = context.companyId();
        try {
            if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new RuntimeException("Nominal harus lebih dari 0");
            if (debitAccountId.equals(creditAccountId)) throw new RuntimeException("Akun Debit dan Kredit tidak boleh sama");

            String docNo = "JV-MAN-" + System.currentTimeMillis();
            Long hdrId = ((Number) em.createNativeQuery(
                "INSERT INTO fin.journal_hdr (tenant_id, company_id, doc_no, doc_date, journal_type, remarks, status) " +
                "VALUES (:tenantId, :compId, :docNo, :docDate, 'MANUAL', :remarks, 'POSTED') RETURNING id")
                .setParameter("tenantId", tenantId).setParameter("compId", compId)
                .setParameter("docNo", docNo).setParameter("docDate", docDate).setParameter("remarks", remarks)
                .getSingleResult()).longValue();

            em.createNativeQuery("INSERT INTO fin.journal_dtl (header_id, account_id, debit, credit, description) VALUES (:hdr, :acc, :amt, 0, :desc)")
                .setParameter("hdr", hdrId).setParameter("acc", debitAccountId).setParameter("amt", amount).setParameter("desc", remarks).executeUpdate();

            em.createNativeQuery("INSERT INTO fin.journal_dtl (header_id, account_id, debit, credit, description) VALUES (:hdr, :acc, 0, :amt, :desc)")
                .setParameter("hdr", hdrId).setParameter("acc", creditAccountId).setParameter("amt", amount).setParameter("desc", remarks).executeUpdate();

            ra.addFlashAttribute("success", "Jurnal " + docNo + " berhasil dibuat.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal buat jurnal: " + e.getMessage());
        }
        return "redirect:/ui/finance/journals";
    }

    @PostMapping("/{id}/post")
    @Transactional
    public String postJournal(@PathVariable Long id, RedirectAttributes ra) {
        try {
            em.createNativeQuery("UPDATE fin.journal_hdr SET status = 'POSTED' WHERE id = :id").setParameter("id", id).executeUpdate();
            ra.addFlashAttribute("success", "Journal berhasil diposting.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/ui/finance/journals";
    }

    @PostMapping("/{id}/unpost")
    @Transactional
    public String unpostJournal(@PathVariable Long id, RedirectAttributes ra) {
        try {
            em.createNativeQuery("UPDATE fin.journal_hdr SET status = 'DRAFT' WHERE id = :id").setParameter("id", id).executeUpdate();
            ra.addFlashAttribute("success", "Journal berhasil di-unpost.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/ui/finance/journals";
    }

    @PostMapping("/{id}/delete")
    @Transactional
    public String deleteJournal(@PathVariable Long id, RedirectAttributes ra) {
        try {
            List<?> statusList = em.createNativeQuery("SELECT status FROM fin.journal_hdr WHERE id = :id").setParameter("id", id).getResultList();
            if (statusList.isEmpty()) throw new RuntimeException("Jurnal tidak ditemukan.");
            String status = statusList.get(0).toString();
            if (!"DRAFT".equals(status)) throw new RuntimeException("Hanya jurnal berstatus DRAFT yang bisa dihapus.");

            em.createNativeQuery("DELETE FROM fin.journal_dtl WHERE header_id = :id").setParameter("id", id).executeUpdate();
            em.createNativeQuery("DELETE FROM fin.journal_hdr WHERE id = :id").setParameter("id", id).executeUpdate();
            ra.addFlashAttribute("success", "Journal berhasil dihapus.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/ui/finance/journals";
    }
}
