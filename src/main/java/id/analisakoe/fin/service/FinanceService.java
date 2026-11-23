package id.analisakoe.fin.service;

import id.analisakoe.common.BusinessException;
import id.analisakoe.fin.entity.ApPayment;
import id.analisakoe.fin.entity.ArCollection;
import id.analisakoe.fin.repository.ApPaymentRepository;
import id.analisakoe.fin.repository.ArCollectionRepository;
import id.analisakoe.fin.repository.CashBankTrxRepository;
import id.analisakoe.fin.entity.CashBankTrx;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class FinanceService {
    private final ArCollectionRepository arRepo;
    private final ApPaymentRepository apRepo;
    private final CashBankTrxRepository cashRepo;
    private final JournalService journalService;

    public FinanceService(ArCollectionRepository arRepo, ApPaymentRepository apRepo, CashBankTrxRepository cashRepo, JournalService journalService) {
        this.arRepo = arRepo; this.apRepo = apRepo; this.cashRepo = cashRepo; this.journalService = journalService;
    }

    @Transactional
    public void saveArCollection(LocalDate date, Long customerId, Long bankId, BigDecimal amount, String remarks) {
        ArCollection ar = new ArCollection();
        ar.setTenantId(1L); ar.setCompanyId(1L);
        ar.setDocNo("AR-" + System.currentTimeMillis());
        ar.setDocDate(date); ar.setCustomerId(customerId); ar.setBankId(bankId);
        ar.setAmount(amount); ar.setRemarks(remarks);
        ar.setStatus("DRAFT");
        arRepo.save(ar);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void postArCollection(Long id) {
        ArCollection ar = arRepo.findById(id).orElseThrow(() -> new BusinessException("AR tidak ditemukan"));
        if (!"DRAFT".equals(ar.getStatus())) throw new BusinessException("Sudah diposting.");

        // Catat Kas Bank
        CashBankTrx cash = new CashBankTrx();
        cash.setTenantId(1L); cash.setCompanyId(1L);
        cash.setDocNo("CB-AR-" + System.currentTimeMillis());
        cash.setDocDate(ar.getDocDate()); cash.setTrxType("IN"); cash.setBankId(ar.getBankId());
        cash.setAmount(ar.getAmount()); cash.setRemarks("Pelunasan Piutang: " + ar.getDocNo());
        cash.setStatus("POSTED");
        cashRepo.save(cash);

        // Jurnal
        journalService.createJournal(ar.getTenantId(), ar.getCompanyId(), ar.getDocDate(), "AR_COLLECT", "Pelunasan Piutang " + ar.getDocNo(), List.of(
            new JournalService.JournalLine("1102", ar.getAmount(), BigDecimal.ZERO, "Kas/Bank"),
            new JournalService.JournalLine("1201", BigDecimal.ZERO, ar.getAmount(), "Piutang Usaha")
        ));
        ar.setStatus("POSTED");
        arRepo.save(ar);
    }

    @Transactional
    public void saveApPayment(LocalDate date, Long supplierId, Long bankId, BigDecimal amount, String remarks) {
        ApPayment ap = new ApPayment();
        ap.setTenantId(1L); ap.setCompanyId(1L);
        ap.setDocNo("AP-" + System.currentTimeMillis());
        ap.setDocDate(date); ap.setSupplierId(supplierId); ap.setBankId(bankId);
        ap.setAmount(amount); ap.setRemarks(remarks);
        ap.setStatus("DRAFT");
        apRepo.save(ap);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void postApPayment(Long id) {
        ApPayment ap = apRepo.findById(id).orElseThrow(() -> new BusinessException("AP tidak ditemukan"));
        if (!"DRAFT".equals(ap.getStatus())) throw new BusinessException("Sudah diposting.");

        CashBankTrx cash = new CashBankTrx();
        cash.setTenantId(1L); cash.setCompanyId(1L);
        cash.setDocNo("CB-AP-" + System.currentTimeMillis());
        cash.setDocDate(ap.getDocDate()); cash.setTrxType("OUT"); cash.setBankId(ap.getBankId());
        cash.setAmount(ap.getAmount()); cash.setRemarks("Pelunasan Hutang: " + ap.getDocNo());
        cash.setStatus("POSTED");
        cashRepo.save(cash);

        journalService.createJournal(ap.getTenantId(), ap.getCompanyId(), ap.getDocDate(), "AP_PAY", "Pelunasan Hutang " + ap.getDocNo(), List.of(
            new JournalService.JournalLine("2101", ap.getAmount(), BigDecimal.ZERO, "Hutang Usaha"),
            new JournalService.JournalLine("1102", BigDecimal.ZERO, ap.getAmount(), "Kas/Bank")
        ));
        ap.setStatus("POSTED");
        apRepo.save(ap);
    }
    // ==================== UNPOST & BATCH ====================
    
    @Transactional
    public void unpostArCollection(Long id) {
        ArCollection ar = arRepo.findById(id).orElseThrow(() -> new BusinessException("AR tidak ditemukan"));
        if (!"POSTED".equals(ar.getStatus())) throw new BusinessException("Hanya status POSTED yang bisa di-Unpost.");

        // 1. Buat Jurnal Pembalik
        journalService.createJournal(ar.getTenantId(), ar.getCompanyId(), ar.getDocDate(), "REVERSE", "Unpost Pelunasan Piutang " + ar.getDocNo(), List.of(
            new JournalService.JournalLine("1201", ar.getAmount(), BigDecimal.ZERO, "Piutang Usaha (Reversal)"),
            new JournalService.JournalLine("1102", BigDecimal.ZERO, ar.getAmount(), "Kas/Bank (Reversal)")
        ));

        // 2. Void Kas Bank (Cari berdasarkan remark)
        List<CashBankTrx> cbs = cashRepo.findAllByOrderByDocDateDesc().stream()
            .filter(c -> c.getRemarks() != null && c.getRemarks().contains(ar.getDocNo())).toList();
        for (CashBankTrx cb : cbs) {
            cb.setStatus("VOID");
            cashRepo.save(cb);
        }

        ar.setStatus("DRAFT");
        arRepo.save(ar);
    }

    @Transactional
    public void unpostApPayment(Long id) {
        ApPayment ap = apRepo.findById(id).orElseThrow(() -> new BusinessException("AP tidak ditemukan"));
        if (!"POSTED".equals(ap.getStatus())) throw new BusinessException("Hanya status POSTED yang bisa di-Unpost.");

        journalService.createJournal(ap.getTenantId(), ap.getCompanyId(), ap.getDocDate(), "REVERSE", "Unpost Pelunasan Hutang " + ap.getDocNo(), List.of(
            new JournalService.JournalLine("1102", ap.getAmount(), BigDecimal.ZERO, "Kas/Bank (Reversal)"),
            new JournalService.JournalLine("2101", BigDecimal.ZERO, ap.getAmount(), "Hutang Usaha (Reversal)")
        ));

        List<CashBankTrx> cbs = cashRepo.findAllByOrderByDocDateDesc().stream()
            .filter(c -> c.getRemarks() != null && c.getRemarks().contains(ap.getDocNo())).toList();
        for (CashBankTrx cb : cbs) {
            cb.setStatus("VOID");
            cashRepo.save(cb);
        }

        ap.setStatus("DRAFT");
        apRepo.save(ap);
    }

    public int batchPostAr(List<Long> ids) {
        int count = 0;
        for (Long id : ids) {
            try { postArCollection(id); count++; } catch (Exception ignored) {}
        }
        return count;
    }

    public int batchPostAp(List<Long> ids) {
        int count = 0;
        for (Long id : ids) {
            try { postApPayment(id); count++; } catch (Exception ignored) {}
        }
        return count;
    }
}