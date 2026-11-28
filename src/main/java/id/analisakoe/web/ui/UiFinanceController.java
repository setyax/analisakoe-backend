package id.analisakoe.web.ui;

import id.analisakoe.context.ContextService;
import id.analisakoe.web.ui.SessionInterceptor;
import id.analisakoe.web.ui.SessionUser;

import id.analisakoe.core.menu.MenuService;
import id.analisakoe.fin.dto.BankBalanceDto;
import id.analisakoe.fin.entity.BankAccount;
import id.analisakoe.fin.entity.CashBankTrx;
import id.analisakoe.fin.repository.ApPaymentRepository;
import id.analisakoe.fin.repository.ArCollectionRepository;
import id.analisakoe.fin.repository.BankAccountRepository;
import id.analisakoe.fin.repository.CashBankTrxRepository;
import id.analisakoe.fin.service.FinanceService;
import id.analisakoe.fin.service.JournalService;
import id.analisakoe.md.repository.CustomerRepository;
import id.analisakoe.md.repository.SupplierRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.support.TransactionTemplate;
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
@RequestMapping("/ui/finance")
public class UiFinanceController {

    @Autowired
    private ContextService context;

    private final BankAccountRepository bankRepo;
    private final CashBankTrxRepository cashRepo;
    private final ArCollectionRepository arRepo;
    private final ApPaymentRepository apRepo;
    private final CustomerRepository custRepo;
    private final SupplierRepository supRepo;
    private final FinanceService finService;
    private final MenuService menuService;
    private final JournalService journalService;

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private TransactionTemplate transactionTemplate;

    public UiFinanceController(BankAccountRepository bankRepo, CashBankTrxRepository cashRepo,
                               ArCollectionRepository arRepo, ApPaymentRepository apRepo,
                               CustomerRepository custRepo, SupplierRepository supRepo,
                               FinanceService finService, MenuService menuService,
                               JournalService journalService) {
        this.bankRepo = bankRepo; this.cashRepo = cashRepo;
        this.arRepo = arRepo; this.apRepo = apRepo;
        this.custRepo = custRepo; this.supRepo = supRepo;
        this.finService = finService; this.menuService = menuService;
        this.journalService = journalService;
    }

    private void addCommonData(HttpSession session, Model model, String title) {
        SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
        Long compId = user.getActiveCompanyId(); // Auto-Injected by Wizard
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", title);
        model.addAttribute("menus", menuService.buildTreeForUser(user.getPermissions()));
    }

    // ==================== BANK ====================
    @GetMapping("/banks")
    public String listBanks(HttpSession session, Model model) {
        addCommonData(session, model, "Bank Accounts");
        model.addAttribute("banks", bankRepo.findByCompanyIdOrderByNameAsc(context.companyId()));
        return "ui/finance/banks";
    }

    // ==================== CASH & BANK ====================
    @GetMapping("/cashbank")
    public String listCashBank(HttpSession session, Model model) {
        addCommonData(session, model, "Cash & Bank Transactions");
        List<CashBankTrx> trxs = cashRepo.findByCompanyIdOrderByDocDateDesc(context.companyId());
        List<BankAccount> banks = bankRepo.findByCompanyIdOrderByNameAsc(context.companyId());
        model.addAttribute("trxs", trxs);
        model.addAttribute("banks", banks);
        model.addAttribute("balances", computeBankBalances(banks, trxs));
        // Index bank by id untuk lookup nama bank di tabel
        Map<Long, String> bankNames = new HashMap<>();
        for (BankAccount b : banks) bankNames.put(b.getId(), b.getName());
        model.addAttribute("bankNames", bankNames);
        return "ui/finance/cashbank_list";
    }

    /**
     * Hitung saldo per bank dari daftar transaksi.
     * Setoran Modal (MODAL) diperlakukan seperti uang masuk.
     */
    private List<BankBalanceDto> computeBankBalances(List<BankAccount> banks, List<CashBankTrx> trxs) {
        List<BankBalanceDto> out = new ArrayList<>();
        for (BankAccount b : banks) {
            BigDecimal totalIn = BigDecimal.ZERO;
            BigDecimal totalOut = BigDecimal.ZERO;
            for (CashBankTrx t : trxs) {
                if (b.getId().equals(t.getBankId()) && !"VOID".equalsIgnoreCase(t.getStatus())) {
                    String tt = t.getTrxType() == null ? "" : t.getTrxType();
                    if ("OUT".equalsIgnoreCase(tt)) {
                        totalOut = totalOut.add(t.getAmount());
                    } else if ("IN".equalsIgnoreCase(tt) || "MODAL".equalsIgnoreCase(tt)) {
                        totalIn = totalIn.add(t.getAmount());
                    }
                }
            }
            out.add(new BankBalanceDto(b.getId(), b.getName(), b.getAccountNo(),
                    totalIn, totalOut, totalIn.subtract(totalOut)));
        }
        return out;
    }

    @GetMapping("/cashbank/new")
    public String newCashBank(HttpSession session, Model model) {
        addCommonData(session, model, "New Cash & Bank Transaction");
        model.addAttribute("banks", bankRepo.findByCompanyIdOrderByNameAsc(context.companyId()));
        model.addAttribute("today", LocalDate.now());
        return "ui/finance/cashbank_form";
    }

    @PostMapping("/cashbank")
    public String saveCashBank(@RequestParam LocalDate docDate, @RequestParam String trxType,
                               @RequestParam Long bankId, @RequestParam BigDecimal amount,
                               @RequestParam(required = false) String remarks, HttpSession session,
                               RedirectAttributes ra) {
        try {
            if (amount == null || amount.signum() <= 0) {
                ra.addFlashAttribute("error", "Amount harus lebih besar dari nol.");
                return "redirect:/ui/finance/cashbank";
            }
            SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
            Long compId = user.getActiveCompanyId();

            // Ambil info bank (nama + COA id) untuk jurnal & tampilan
            List<Object[]> bankInfo = em.createNativeQuery(
                "SELECT b.name, c.id, c.code FROM fin.bank_account b " +
                "LEFT JOIN fin.chart_of_account c ON c.id = b.coa_id " +
                "WHERE b.id = :id AND b.tenant_id = :tenantId AND b.company_id = :compId")
                .setParameter("id", bankId)
                .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
                .getResultList();
            final String bankName = bankInfo.isEmpty() ? "Bank#" + bankId
                : (bankInfo.get(0)[0] != null ? bankInfo.get(0)[0].toString() : "Bank#" + bankId);
            final Long bankCoaId;
            final String bankCoaCode;
            if (bankInfo.isEmpty() || bankInfo.get(0)[1] == null) {
                // Default ke akun 1102 (Bank BCA) bila bank belum terhubung ke COA
                List<Object[]> def = em.createNativeQuery(
                    "SELECT id, code FROM fin.chart_of_account WHERE tenant_id = :tenantId AND company_id = :compId AND code = '1102'")
                    .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId()).getResultList();
                bankCoaId = def.isEmpty() ? null : ((Number) def.get(0)[0]).longValue();
                bankCoaCode = def.isEmpty() ? "1102" : (String) def.get(0)[1];
            } else {
                bankCoaId = ((Number) bankInfo.get(0)[1]).longValue();
                bankCoaCode = bankInfo.get(0)[2] != null ? bankInfo.get(0)[2].toString() : "1102";
            }

            // Simpan cash_bank_trx dulu agar punya ID utk ref_type/ref_id jurnal
            final BigDecimal amtFinal = amount;
            final LocalDate dateFinal = docDate;
            final String remarksFinal = remarks;
            final String trxTypeFinal = trxType;
            final Long[] trxIdHolder = new Long[1];

            transactionTemplate.executeWithoutResult(tx -> {
                String docNo = "CB-" + System.currentTimeMillis();
                Number newId = (Number) em.createNativeQuery(
                    "INSERT INTO fin.cash_bank_trx (tenant_id, company_id, doc_no, doc_date, trx_type, bank_id, amount, remarks, status) " +
                    "VALUES (:tenantId, :cid, :doc, :date, :type, :bid, :amt, :rem, 'POSTED') RETURNING id")
                    .setParameter("cid", compId)
                    .setParameter("doc", docNo)
                    .setParameter("date", dateFinal)
                    .setParameter("type", trxTypeFinal)
                    .setParameter("bid", bankId)
                    .setParameter("amt", amtFinal)
                    .setParameter("rem", remarksFinal)
                    .setParameter("tenantId", context.tenantId()).getSingleResult();
                trxIdHolder[0] = newId.longValue();
            });
            final Long trxId = trxIdHolder[0];

            if ("MODAL".equalsIgnoreCase(trxTypeFinal)) {
                String note = "Setoran modal ke " + bankName
                    + (remarksFinal != null && !remarksFinal.isBlank() ? " — " + remarksFinal : "");
                final Long coaId3101;
                List<Object[]> r3101 = em.createNativeQuery(
                    "SELECT id FROM fin.chart_of_account WHERE tenant_id = :tenantId AND company_id = :compId AND code = '3101'")
            .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId()).getResultList();
                if (r3101.isEmpty())
                    throw new IllegalStateException("Akun COA 3101 (Modal Disetor) tidak ditemukan.");
                coaId3101 = ((Number) r3101.get(0)[0]).longValue();

                transactionTemplate.executeWithoutResult(tx -> {
                    Long hdrId = ((Number) em.createNativeQuery(
                        "INSERT INTO fin.journal_hdr (tenant_id, company_id, doc_no, doc_date, journal_type, remarks, status, ref_type, ref_id) " +
                        "VALUES (:tenantId, :cid, :docNo, :docDate, 'MODAL', :remarks, 'POSTED', 'CASH_BANK', :refId) RETURNING id")
                        .setParameter("cid", compId)
                        .setParameter("docNo", "JV-MODAL-" + trxId)
                        .setParameter("docDate", dateFinal)
                        .setParameter("remarks", note)
                        .setParameter("refId", trxId)
                        .setParameter("tenantId", context.tenantId()).getSingleResult()).longValue();

                    if (bankCoaId != null) {
                        em.createNativeQuery(
                            "INSERT INTO fin.journal_dtl (header_id, account_id, debit, credit, description) " +
                            "VALUES (:hdr, :acc, :dr, 0, :desc)")
                            .setParameter("hdr", hdrId)
                            .setParameter("acc", bankCoaId)
                            .setParameter("dr", amtFinal)
                            .setParameter("desc", "Setoran modal masuk " + bankName)
                            .executeUpdate();
                    }
                    em.createNativeQuery(
                        "INSERT INTO fin.journal_dtl (header_id, account_id, debit, credit, description) " +
                        "VALUES (:hdr, :acc, 0, :cr, :desc)")
                        .setParameter("hdr", hdrId)
                        .setParameter("acc", coaId3101)
                        .setParameter("cr", amtFinal)
                        .setParameter("desc", "Modal Disetor (Ekuitas)")
                        .executeUpdate();
                });
                ra.addFlashAttribute("success", "Setoran modal berhasil disimpan & dijurnal: Debit " + bankCoaCode
                    + " (" + bankName + ") / Kredit 3101 Modal Disetor.");
            } else {
                ra.addFlashAttribute("success", "Transaksi Kas/Bank berhasil disimpan.");
            }
        } catch (Exception e) { ra.addFlashAttribute("error", "Gagal: " + rootCause(e)); }
        return "redirect:/ui/finance/cashbank";
    }

    // ==================== CashBank: VIEW detail ====================
    @GetMapping("/cashbank/{id}")
    public String viewCashBank(@PathVariable Long id, HttpSession session, Model model) {
        CashBankTrx t = cashRepo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Transaksi #" + id + " tidak ditemukan."));
        addCommonData(session, model, "Cash & Bank Transaction");
        model.addAttribute("trx", t);
        model.addAttribute("banks", bankRepo.findByCompanyIdOrderByNameAsc(context.companyId()));
        model.addAttribute("journalLink", findRelatedJournal(id, t.getTrxType()));
        return "ui/finance/cashbank_view";
    }

    // ==================== CashBank: EDIT form ====================
    @GetMapping("/cashbank/{id}/edit")
    public String editCashBank(@PathVariable Long id, HttpSession session, Model model) {
        CashBankTrx t = cashRepo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Transaksi #" + id + " tidak ditemukan."));
        if ("VOID".equalsIgnoreCase(t.getStatus())) {
            throw new IllegalStateException("Transaksi VOID tidak dapat diedit. Hapus dulu untuk membuat ulang.");
        }
        addCommonData(session, model, "Edit Cash & Bank Transaction");
        model.addAttribute("trx", t);
        model.addAttribute("banks", bankRepo.findByCompanyIdOrderByNameAsc(context.companyId()));
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("mode", "edit");
        return "ui/finance/cashbank_form";
    }

    // ==================== CashBank: UPDATE ====================
    @PostMapping("/cashbank/{id}")
    public String updateCashBank(@PathVariable Long id, @RequestParam LocalDate docDate,
                                 @RequestParam Long bankId, @RequestParam BigDecimal amount,
                                 @RequestParam(required = false) String remarks, HttpSession session,
                                 RedirectAttributes ra) {
        try {
            if (amount == null || amount.signum() <= 0) {
                ra.addFlashAttribute("error", "Amount harus lebih besar dari nol.");
                return "redirect:/ui/finance/cashbank/" + id + "/edit";
            }
            SessionUser user = (SessionUser) session.getAttribute(SessionInterceptor.SESSION_USER);
            Long compId = user.getActiveCompanyId();

            final BigDecimal amtFinal = amount;
            final LocalDate dateFinal = docDate;
            final String remarksFinal = remarks;
            final Long bankIdFinal = bankId;

            transactionTemplate.executeWithoutResult(tx -> {
                CashBankTrx orig = cashRepo.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Transaksi #" + id + " tidak ditemukan."));
                if ("VOID".equalsIgnoreCase(orig.getStatus()))
                    throw new IllegalStateException("Transaksi VOID tidak dapat diedit.");

                // Untuk MODAL: void jurnal lama & buat jurnal baru agar saldo bank akurat.
                if ("MODAL".equalsIgnoreCase(orig.getTrxType())) {
                    reverseModalJournal(id, orig, "Edit jurnal setoran modal");
                }

                em.createNativeQuery(
                    "UPDATE fin.cash_bank_trx SET doc_date=:d, bank_id=:b, amount=:a, remarks=:r WHERE id=:id")
                    .setParameter("d", dateFinal)
                    .setParameter("b", bankIdFinal)
                    .setParameter("a", amtFinal)
                    .setParameter("r", remarksFinal)
                    .setParameter("id", id)
                    .executeUpdate();

                if ("MODAL".equalsIgnoreCase(orig.getTrxType())) {
                    recreateModalJournal(id, bankIdFinal, amtFinal, dateFinal, remarksFinal, compId);
                }
            });
            ra.addFlashAttribute("success", "Transaksi #" + id + " berhasil diperbarui.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal update: " + rootCause(e));
        }
        return "redirect:/ui/finance/cashbank";
    }

    // ==================== CashBank: VOID (dengan pembalikan jurnal untuk MODAL) ====================
    @PostMapping("/cashbank/{id}/void")
    public String voidCashBank(@PathVariable Long id, RedirectAttributes ra) {
        try {
            transactionTemplate.executeWithoutResult(tx -> {
                Object[] row = (Object[]) em.createNativeQuery(
                    "SELECT doc_no, status, trx_type, amount, remarks, tenant_id, company_id " +
                    "FROM fin.cash_bank_trx WHERE id = :id")
                    .setParameter("id", id).getSingleResult();
                String docNo = row[0].toString();
                String status = row[1].toString();
                String trxType = row[2] != null ? row[2].toString() : "";
                BigDecimal amount = new BigDecimal(row[3].toString());
                String remarks = row[4] != null ? row[4].toString() : "";
                Long tenantId = ((Number) row[5]).longValue();
                Long compId = ((Number) row[6]).longValue();

                if ("VOID".equalsIgnoreCase(status))
                    throw new IllegalStateException("Transaksi " + docNo + " sudah VOID.");

                if ("MODAL".equalsIgnoreCase(trxType)) {
                    long masuk = (((Number) em.createNativeQuery(
                        "SELECT COALESCE(SUM(debit),0) FROM fin.journal_dtl d " +
                        "JOIN fin.journal_hdr h ON h.id = d.header_id " +
                        "WHERE h.ref_type='CASH_BANK' AND h.ref_id=:id AND d.debit>0")
                        .setParameter("id", id).getSingleResult()).longValue());
                    long keluar = (((Number) em.createNativeQuery(
                        "SELECT COALESCE(SUM(credit),0) FROM fin.journal_dtl d " +
                        "JOIN fin.journal_hdr h ON h.id = d.header_id " +
                        "WHERE h.ref_type='CASH_BANK' AND h.ref_id=:id AND d.credit>0")
                        .setParameter("id", id).getSingleResult()).longValue());
                    long bankAccId = (((Number) em.createNativeQuery(
                        "SELECT account_id FROM fin.journal_dtl d " +
                        "JOIN fin.journal_hdr h ON h.id = d.header_id " +
                        "WHERE h.ref_type='CASH_BANK' AND h.ref_id=:id AND d.debit>0 LIMIT 1")
                        .setParameter("id", id).getSingleResult()).longValue());
                    long eqAccId = (((Number) em.createNativeQuery(
                        "SELECT account_id FROM fin.journal_dtl d " +
                        "JOIN fin.journal_hdr h ON h.id = d.header_id " +
                        "WHERE h.ref_type='CASH_BANK' AND h.ref_id=:id AND d.credit>0 LIMIT 1")
                        .setParameter("id", id).getSingleResult()).longValue());

                    Long revHvId = ((Number) em.createNativeQuery(
                        "INSERT INTO fin.journal_hdr (tenant_id, company_id, doc_no, doc_date, journal_type, remarks, status, ref_type, ref_id) " +
                        "VALUES (:tid, :cid, :doc, CURRENT_DATE, 'VOID_MODAL', :rem, 'POSTED', 'CASH_BANK_VOID', :rid) RETURNING id")
                        .setParameter("tid", tenantId).setParameter("cid", compId)
                        .setParameter("doc", "REV-MODAL-" + id)
                        .setParameter("rem", "Void setoran modal " + docNo + (remarks.isBlank() ? "" : " — " + remarks))
                        .setParameter("rid", id).getSingleResult()).longValue();

                    em.createNativeQuery(
                        "INSERT INTO fin.journal_dtl (header_id, account_id, debit, credit, description) " +
                        "VALUES (:h, :a, :dr, 0, 'Void setoran modal')")
                        .setParameter("h", revHvId).setParameter("a", eqAccId)
                        .setParameter("dr", amount).executeUpdate();
                    em.createNativeQuery(
                        "INSERT INTO fin.journal_dtl (header_id, account_id, debit, credit, description) " +
                        "VALUES (:h, :a, 0, :cr, 'Void setoran modal')")
                        .setParameter("h", revHvId).setParameter("a", bankAccId)
                        .setParameter("cr", amount).executeUpdate();
                }

                em.createNativeQuery(
                    "UPDATE fin.cash_bank_trx SET status = 'VOID' WHERE id = :id")
                    .setParameter("id", id).executeUpdate();
            });
            ra.addFlashAttribute("success", "Transaksi berhasil di-VOID.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal void: " + rootCause(e));
        }
        return "redirect:/ui/finance/cashbank";
    }

    // ==================== CashBank: DELETE (hanya yang sudah VOID) ====================
    @PostMapping("/cashbank/{id}/delete")
    public String deleteCashBank(@PathVariable Long id, RedirectAttributes ra) {
        try {
            transactionTemplate.executeWithoutResult(tx -> {
                Object[] row = (Object[]) em.createNativeQuery(
                    "SELECT doc_no, status, trx_type FROM fin.cash_bank_trx WHERE id = :id")
                    .setParameter("id", id).getSingleResult();
                String docNo = row[0].toString();
                String status = row[1].toString();

                if (!"VOID".equalsIgnoreCase(status))
                    throw new IllegalStateException("Transaksi " + docNo + " masih POSTED. Void dulu sebelum menghapus.");

                // Hapus jurnal pembalik yang terkait (jika ada)
                List<Integer> hdrIds = em.createNativeQuery(
                    "SELECT id FROM fin.journal_hdr WHERE ref_type IN ('CASH_BANK','CASH_BANK_VOID') AND ref_id = :id")
                    .setParameter("id", id).getResultList();
                for (Object hdrId : hdrIds) {
                    em.createNativeQuery("DELETE FROM fin.journal_dtl WHERE header_id = :h")
                        .setParameter("h", ((Number) hdrId).longValue()).executeUpdate();
                    em.createNativeQuery("DELETE FROM fin.journal_hdr WHERE id = :h")
                        .setParameter("h", ((Number) hdrId).longValue()).executeUpdate();
                }

                em.createNativeQuery("DELETE FROM fin.cash_bank_trx WHERE id = :id")
                    .setParameter("id", id).executeUpdate();
            });
            ra.addFlashAttribute("success", "Transaksi berhasil dihapus.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Gagal hapus: " + rootCause(e));
        }
        return "redirect:/ui/finance/cashbank";
    }

    // ==================== CashBank: Reverse jurnal MODAL (untuk edit) ====================
    private void reverseModalJournal(Long trxId, CashBankTrx orig, String reason) {
        Object[] hdr = (Object[]) em.createNativeQuery(
            "SELECT id, tenant_id, company_id FROM fin.journal_hdr " +
            "WHERE ref_type = 'CASH_BANK' AND ref_id = :id AND journal_type = 'MODAL' AND status = 'POSTED'")
            .setParameter("id", trxId).getSingleResult();
        final Long origHvId = ((Number) hdr[0]).longValue();
        final Long tenantId = ((Number) hdr[1]).longValue();
        final Long compId = ((Number) hdr[2]).longValue();

        Long revHvId = ((Number) em.createNativeQuery(
            "INSERT INTO fin.journal_hdr (tenant_id, company_id, doc_no, doc_date, journal_type, remarks, status, ref_type, ref_id) " +
            "VALUES (:tid, :cid, :doc, CURRENT_DATE, 'VOID_MODAL', :rem, 'POSTED', 'CASH_BANK_VOID', :rid) RETURNING id")
            .setParameter("tid", tenantId).setParameter("cid", compId)
            .setParameter("doc", "REV-MODAL-EDIT-" + trxId)
            .setParameter("rem", reason + " (orig " + orig.getDocNo() + ")")
            .setParameter("rid", trxId).getSingleResult()).longValue();

        @SuppressWarnings("unchecked")
        List<Object[]> lines = em.createNativeQuery(
            "SELECT account_id, debit, credit, description FROM fin.journal_dtl WHERE header_id = :h")
            .setParameter("h", origHvId).getResultList();
        for (Object[] line : lines) {
            Long accId = ((Number) line[0]).longValue();
            BigDecimal dr = line[1] != null ? new BigDecimal(line[1].toString()) : BigDecimal.ZERO;
            BigDecimal cr = line[2] != null ? new BigDecimal(line[2].toString()) : BigDecimal.ZERO;
            em.createNativeQuery(
                "INSERT INTO fin.journal_dtl (header_id, account_id, debit, credit, description) " +
                "VALUES (:h, :a, :dr, :cr, :d)")
                .setParameter("h", revHvId).setParameter("a", accId)
                .setParameter("dr", cr).setParameter("cr", dr)
                .setParameter("d", "Reversal edit: " + (line[3] != null ? line[3].toString() : ""))
                .executeUpdate();
        }
        em.createNativeQuery("UPDATE fin.journal_hdr SET status='VOID' WHERE id = :h")
            .setParameter("h", origHvId).executeUpdate();
    }

    // ==================== CashBank: Buat ulang jurnal MODAL setelah edit ====================
    @SuppressWarnings("unchecked")
    private void recreateModalJournal(Long trxId, Long bankId, BigDecimal amount,
                                      LocalDate docDate, String remarks, Long compId) {
        List<Object[]> bankInfo = em.createNativeQuery(
            "SELECT b.name, c.id, c.code FROM fin.bank_account b " +
            "LEFT JOIN fin.chart_of_account c ON c.id = b.coa_id " +
                "WHERE b.id = :id AND b.tenant_id = :tenantId AND b.company_id = :compId")
            .setParameter("id", bankId)
            .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId())
            .getResultList();
        if (bankInfo.isEmpty())
            throw new IllegalArgumentException("Bank #" + bankId + " tidak ditemukan.");
        String bankName = bankInfo.get(0)[0] != null ? bankInfo.get(0)[0].toString() : ("Bank#" + bankId);
        Long bankCoaId = bankInfo.get(0)[1] != null ? ((Number) bankInfo.get(0)[1]).longValue() : null;
        String bankCoaCode = bankInfo.get(0)[2] != null ? bankInfo.get(0)[2].toString() : "1102";

        if (bankCoaId == null) {
            List<Object[]> def = em.createNativeQuery(
                "SELECT id FROM fin.chart_of_account WHERE tenant_id = :tenantId AND company_id = :compId AND code = '1102'")
                    .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId()).getResultList();
            bankCoaId = def.isEmpty() ? null : ((Number) def.get(0)[0]).longValue();
        }
        List<Object[]> r3101 = em.createNativeQuery(
            "SELECT id FROM fin.chart_of_account WHERE tenant_id = :tenantId AND company_id = :compId AND code = '3101'")
            .setParameter("tenantId", context.tenantId()).setParameter("compId", context.companyId()).getResultList();
        if (r3101.isEmpty())
            throw new IllegalStateException("Akun COA 3101 (Modal Disetor) tidak ditemukan.");
        Long coaId3101 = ((Number) r3101.get(0)[0]).longValue();

        String note = "Setoran modal ke " + bankName
            + (remarks != null && !remarks.isBlank() ? " — " + remarks : "");

        final Long bankCoaIdFinal = bankCoaId;
        final Long coaId3101Final = coaId3101;
        final BigDecimal amtFinal = amount;
        final LocalDate dateFinal = docDate;
        final String bankCoaCodeFinal = bankCoaCode;
        final String bankNameFinal = bankName;
        transactionTemplate.executeWithoutResult(tx -> {
            Long hdrId = ((Number) em.createNativeQuery(
                "INSERT INTO fin.journal_hdr (tenant_id, company_id, doc_no, doc_date, journal_type, remarks, status, ref_type, ref_id) " +
                "VALUES (:tenantId, :cid, :docNo, :docDate, 'MODAL', :remarks, 'POSTED', 'CASH_BANK', :refId) RETURNING id")
                .setParameter("cid", compId)
                .setParameter("docNo", "JV-MODAL-" + trxId + "-V2")
                .setParameter("docDate", dateFinal)
                .setParameter("remarks", note + " (edited)")
                .setParameter("refId", trxId)
                .setParameter("tenantId", context.tenantId()).getSingleResult()).longValue();

            if (bankCoaIdFinal != null) {
                em.createNativeQuery(
                    "INSERT INTO fin.journal_dtl (header_id, account_id, debit, credit, description) " +
                    "VALUES (:h, :a, :dr, 0, :d)")
                    .setParameter("h", hdrId).setParameter("a", bankCoaIdFinal)
                    .setParameter("dr", amtFinal)
                    .setParameter("d", "Setoran modal masuk " + bankNameFinal)
                    .executeUpdate();
            }
            em.createNativeQuery(
                "INSERT INTO fin.journal_dtl (header_id, account_id, debit, credit, description) " +
                "VALUES (:h, :a, 0, :cr, :d)")
                .setParameter("h", hdrId).setParameter("a", coaId3101Final)
                .setParameter("cr", amtFinal)
                .setParameter("d", "Modal Disetor (Ekuitas)")
                .executeUpdate();
        });
    }

    // ==================== Cari jurnal terkait untuk tampilan view ====================
    private Map<String, Object> findRelatedJournal(Long trxId, String trxType) {
        try {
            List<Object[]> rows = em.createNativeQuery(
                "SELECT id, doc_no, doc_date, journal_type, remarks FROM fin.journal_hdr " +
                "WHERE ref_type IN ('CASH_BANK','CASH_BANK_VOID') AND ref_id = :id ORDER BY id")
                .setParameter("id", trxId).getResultList();
            if (rows.isEmpty()) return null;
            Object[] first = rows.get(0);
            Map<String, Object> m = new HashMap<>();
            m.put("id", ((Number) first[0]).longValue());
            m.put("docNo", first[1].toString());
            m.put("docDate", first[2] != null ? first[2].toString() : "-");
            m.put("journalType", first[3] != null ? first[3].toString() : "-");
            m.put("remarks", first[4] != null ? first[4].toString() : "-");
            return m;
        } catch (Exception ignore) { return null; }
    }

    private static String rootCause(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
        return cur.getMessage() != null ? cur.getMessage() : cur.toString();
    }

    // ==================== AR COLLECTION ====================
    @GetMapping("/ar")
    public String listAr(HttpSession session, Model model) {
        addCommonData(session, model, "AR Collections");
        model.addAttribute("collections", arRepo.findByCompanyIdOrderByDocDateDesc(context.companyId()));
        return "ui/finance/ar_list";
    }

    @GetMapping("/ar/new")
    public String newAr(HttpSession session, Model model) {
        addCommonData(session, model, "New AR Collection");
        model.addAttribute("customers", custRepo.findByTenantIdAndDeletedAtIsNull(context.tenantId(), PageRequest.of(0, 100)).getContent());
        model.addAttribute("banks", bankRepo.findByCompanyIdOrderByNameAsc(context.companyId()));
        model.addAttribute("today", LocalDate.now());
        return "ui/finance/ar_form";
    }

    @PostMapping("/ar")
    public String saveAr(@RequestParam LocalDate docDate, @RequestParam Long customerId,
                         @RequestParam Long bankId, @RequestParam BigDecimal amount,
                         @RequestParam(required = false) String remarks, RedirectAttributes ra) {
        try {
            finService.saveArCollection(docDate, customerId, bankId, amount, remarks);
            ra.addFlashAttribute("success", "Pelunasan Piutang berhasil dicatat.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/ui/finance/ar";
    }

    // ==================== AP PAYMENT ====================
    @GetMapping("/ap")
    public String listAp(HttpSession session, Model model) {
        addCommonData(session, model, "AP Payments");
        model.addAttribute("payments", apRepo.findByCompanyIdOrderByDocDateDesc(context.companyId()));
        return "ui/finance/ap_list";
    }

    @GetMapping("/ap/new")
    public String newAp(HttpSession session, Model model) {
        addCommonData(session, model, "New AP Payment");
        model.addAttribute("suppliers", supRepo.findByTenantIdAndDeletedAtIsNull(context.tenantId(), PageRequest.of(0, 100)).getContent());
        model.addAttribute("banks", bankRepo.findByCompanyIdOrderByNameAsc(context.companyId()));
        model.addAttribute("today", LocalDate.now());
        return "ui/finance/ap_form";
    }

    @PostMapping("/ap")
    public String saveAp(@RequestParam LocalDate docDate, @RequestParam Long supplierId,
                         @RequestParam Long bankId, @RequestParam BigDecimal amount,
                         @RequestParam(required = false) String remarks, RedirectAttributes ra) {
        try {
            finService.saveApPayment(docDate, supplierId, bankId, amount, remarks);
            ra.addFlashAttribute("success", "Pelunasan Hutang berhasil dicatat.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/ui/finance/ap";
    }

    // ==================== BATCH POSTING ====================
    @GetMapping("/batch-posting")
    public String batchPosting(HttpSession session, Model model) {
        addCommonData(session, model, "Batch Posting");
        var ars = arRepo.findByCompanyIdOrderByDocDateDesc(context.companyId()).stream().filter(a -> "DRAFT".equals(a.getStatus())).toList();
        var aps = apRepo.findByCompanyIdOrderByDocDateDesc(context.companyId()).stream().filter(a -> "DRAFT".equals(a.getStatus())).toList();
        
        model.addAttribute("draftArs", ars);
        model.addAttribute("draftAps", aps);
        return "ui/finance/batch_posting";
    }

    @PostMapping("/batch-posting/post")
    public String processBatchPost(@RequestParam(value = "arIds", required = false) List<Long> arIds,
                                   @RequestParam(value = "apIds", required = false) List<Long> apIds,
                                   RedirectAttributes ra) {
        try {
            int total = 0;
            if (arIds != null) total += finService.batchPostAr(arIds);
            if (apIds != null) total += finService.batchPostAp(apIds);
            
            ra.addFlashAttribute("success", total + " transaksi berhasil di-Posting.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return "redirect:/ui/finance/batch-posting";
    }
}