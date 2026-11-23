package id.analisakoe.hr.service;

import id.analisakoe.common.BusinessException;
import id.analisakoe.fin.entity.CashBankTrx;
import id.analisakoe.fin.repository.CashBankTrxRepository;
import id.analisakoe.fin.service.JournalService;
import id.analisakoe.hr.entity.CashAdvance;
import id.analisakoe.hr.repository.CashAdvanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class CashAdvanceService {
    private final CashAdvanceRepository repo;
    private final CashBankTrxRepository cashRepo;
    private final JournalService journalService;

    public CashAdvanceService(CashAdvanceRepository repo, CashBankTrxRepository cashRepo, JournalService journalService) {
        this.repo = repo; this.cashRepo = cashRepo; this.journalService = journalService;
    }

    @Transactional
    // UBAH SIGNATURE METHOD MENJADI SEPERTI INI:
public void create(Long companyId, LocalDate docDate, Long employeeId, Long bankId, BigDecimal amount, String remarks) {
    
    // ... (kode pembuatan nomor dokumen / docNo) ...

    CashAdvance ca = new CashAdvance();
    ca.setCompanyId(companyId); // <-- TAMBAHKAN INI AGAR TERSIMPAN KE CABANG YANG BENAR
    ca.setDocDate(docDate);
    ca.setEmployeeId(employeeId);
    ca.setBankId(bankId);
    ca.setAmount(amount);
    ca.setRemarks(remarks);
    ca.setStatus("DRAFT");
    
    // ... (kode penyimpanan repo.save(ca) atau EntityManager) ...
    repo.save(ca);
}

    @Transactional
    public void post(Long id) {
        CashAdvance ca = repo.findById(id).orElseThrow(() -> new BusinessException("Kas Bon tidak ditemukan"));
        if (!"DRAFT".equals(ca.getStatus())) throw new BusinessException("Hanya Kas Bon DRAFT yang bisa diposting.");

        // 1. Catat Kas Keluar
        CashBankTrx cash = new CashBankTrx();
        cash.setTenantId(1L); cash.setCompanyId(1L);
        cash.setDocNo("CB-CA-" + System.currentTimeMillis());
        cash.setDocDate(ca.getDocDate()); cash.setTrxType("OUT"); cash.setBankId(ca.getBankId());
        cash.setAmount(ca.getAmount()); cash.setRemarks("Pencairan Kas Bon: " + ca.getDocNo());
        cash.setStatus("POSTED");
        cashRepo.save(cash);

        // 2. Jurnal: Debit Piutang Karyawan (1103), Kredit Bank (1102)
        journalService.createJournal(ca.getTenantId(), ca.getCompanyId(), ca.getDocDate(), "CASH_ADVANCE", "Pencairan Kas Bon " + ca.getDocNo(), List.of(
            new JournalService.JournalLine("1103", ca.getAmount(), BigDecimal.ZERO, "Piutang Karyawan"),
            new JournalService.JournalLine("1102", BigDecimal.ZERO, ca.getAmount(), "Kas/Bank")
        ));

        ca.setStatus("POSTED");
        repo.save(ca);
    }

    /** Batal cair: hapus jurnal & kas keluar, kembali ke DRAFT. */
    @Transactional
    public void unpost(Long id) {
        CashAdvance ca = repo.findById(id).orElseThrow(() -> new BusinessException("Kas Bon tidak ditemukan"));
        if (!"POSTED".equals(ca.getStatus()))
            throw new BusinessException("Hanya Kas Bon berstatus POSTED yang dapat dibatalkan cair.");

        int deletedJv = journalService.deleteByTypeAndRemarks("CASH_ADVANCE", "Pencairan Kas Bon " + ca.getDocNo());
        if (deletedJv == 0)
            throw new BusinessException("Jurnal pencairan tidak ditemukan. Kas Bon tidak diubah.");
        cashRepo.deleteAll(cashRepo.findByRemarks("Pencairan Kas Bon: " + ca.getDocNo()));

        ca.setStatus("DRAFT");
        repo.save(ca);
    }

    /** Batal LPJ: hapus jurnal LPJ & kas masuk sisa, kembali ke POSTED. */
    @Transactional
    public void unpostSettle(Long id) {
        CashAdvance ca = repo.findById(id).orElseThrow(() -> new BusinessException("Kas Bon tidak ditemukan"));
        if (!"SETTLED".equals(ca.getStatus()))
            throw new BusinessException("Hanya Kas Bon berstatus SETTLED yang dapat dibatalkan LPJ-nya.");

        int deletedJv = journalService.deleteByTypeAndRemarks("LPJ", "LPJ Kas Bon " + ca.getDocNo());
        if (deletedJv == 0)
            throw new BusinessException("Jurnal LPJ tidak ditemukan. Kas Bon tidak diubah.");
        cashRepo.deleteAll(cashRepo.findByRemarks("Pengembalian Sisa Kas Bon: " + ca.getDocNo()));

        ca.setSettlementDate(null);
        ca.setExpenseAmount(null);
        ca.setReturnAmount(null);
        ca.setStatus("POSTED");
        repo.save(ca);
    }
    @Transactional
    public void settle(Long id, LocalDate settleDate, BigDecimal expenseAmount, BigDecimal returnAmount, String remarks) {
        CashAdvance ca = repo.findById(id).orElseThrow(() -> new BusinessException("Kas Bon tidak ditemukan"));
        if (!"POSTED".equals(ca.getStatus())) throw new BusinessException("Hanya Kas Bon POSTED yang bisa di-LPJ.");

        ca.setSettlementDate(settleDate);
        ca.setExpenseAmount(expenseAmount);
        ca.setReturnAmount(returnAmount);
        ca.setStatus("SETTLED");
        if (remarks != null) ca.setRemarks(ca.getRemarks() + " | LPJ: " + remarks);
        repo.save(ca);

        // Jika ada sisa uang kembali, catat Kas Masuk
        if (returnAmount.compareTo(BigDecimal.ZERO) > 0) {
            CashBankTrx cash = new CashBankTrx();
            cash.setTenantId(1L); cash.setCompanyId(1L);
            cash.setDocNo("CB-LPJ-" + System.currentTimeMillis());
            cash.setDocDate(settleDate); cash.setTrxType("IN"); cash.setBankId(ca.getBankId());
            cash.setAmount(returnAmount); cash.setRemarks("Pengembalian Sisa Kas Bon: " + ca.getDocNo());
            cash.setStatus("POSTED");
            cashRepo.save(cash);
        }

        // JURNAL LPJ:
        // 1. Debit Beban Perjalanan (6104) sebesar expenseAmount
        // 2. Debit Kas (1102) sebesar returnAmount (jika ada)
        // 3. Kredit Piutang Karyawan (1103) sebesar amount (nilai kas bon awal)
        
        java.util.List<JournalService.JournalLine> lines = new java.util.ArrayList<>();
        lines.add(new JournalService.JournalLine("6104", expenseAmount, BigDecimal.ZERO, "Beban Perjalanan Dinas"));
        
        if (returnAmount.compareTo(BigDecimal.ZERO) > 0) {
            lines.add(new JournalService.JournalLine("1102", returnAmount, BigDecimal.ZERO, "Kas Kembali"));
        }
        
        lines.add(new JournalService.JournalLine("1103", BigDecimal.ZERO, ca.getAmount(), "Pelunasan Piutang Karyawan"));

        journalService.createJournal(ca.getTenantId(), ca.getCompanyId(), settleDate, "LPJ", "LPJ Kas Bon " + ca.getDocNo(), lines);
    }
}