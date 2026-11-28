package id.analisakoe.fin.service;

import id.analisakoe.context.ContextService;
import org.springframework.beans.factory.annotation.Autowired;
import id.analisakoe.core.service.NumberRangeService;

import id.analisakoe.common.BusinessException;
import id.analisakoe.fin.entity.ChartOfAccount;
import id.analisakoe.fin.entity.JournalDtl;
import id.analisakoe.fin.entity.JournalHdr;
import id.analisakoe.fin.repository.ChartOfAccountRepository;
import id.analisakoe.fin.repository.JournalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class JournalService {

    @Autowired
    private ContextService context;

    @Autowired
    private NumberRangeService numberRangeService;
    private final JournalRepository repo;
    private final ChartOfAccountRepository coaRepo;
    private final FiscalPeriodGuardService fiscalPeriodGuard;

    public JournalService(JournalRepository repo, ChartOfAccountRepository coaRepo, FiscalPeriodGuardService fiscalPeriodGuard)  {
        this.repo = repo; this.coaRepo = coaRepo; this.fiscalPeriodGuard = fiscalPeriodGuard;
    }

    // Menggunakan String accountCode (misal "4101", "1201") bukan Long ID
    public record JournalLine(String accountCode, BigDecimal debit, BigDecimal credit, String description) {}

    @Transactional
    public void createJournal(LocalDate date, String type, String remarks, List<JournalLine> lines) {
        createJournal(context.tenantId(), context.companyId(), date, type, remarks, lines);
    }

    /** Versi eksplisit: jurnal mengikuti company dokumen asal. */
    @Transactional
    public void createJournal(Long tenantId, Long companyId, LocalDate date, String type, String remarks, List<JournalLine> lines) {
        fiscalPeriodGuard.assertOpen(tenantId, companyId, date, "JOURNAL/" + type, null);
        JournalHdr hdr = new JournalHdr();
        hdr.setTenantId(tenantId); hdr.setCompanyId(companyId);
        hdr.setDocNo("DOC-" + System.currentTimeMillis());
        hdr.setDocDate(date); hdr.setJournalType(type); hdr.setRemarks(remarks);

        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        for (var l : lines) {
            // Cari akun berdasarkan KODE (bukan ID)
            ChartOfAccount coa = coaRepo.findByCode(l.accountCode())
                .orElseThrow(() -> new BusinessException("Akun COA dengan kode " + l.accountCode() + " tidak ditemukan."));

            JournalDtl d = new JournalDtl();
            d.setAccountId(coa.getId());
            d.setDebit(l.debit() != null ? l.debit() : BigDecimal.ZERO);
            d.setCredit(l.credit() != null ? l.credit() : BigDecimal.ZERO);
            d.setDescription(l.description());
            hdr.addDetail(d);

            totalDebit = totalDebit.add(d.getDebit());
            totalCredit = totalCredit.add(d.getCredit());
        }

        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new BusinessException("Jurnal tidak seimbang! Total Debit: " + totalDebit + " != Total Kredit: " + totalCredit);
        }

        repo.save(hdr);
    }

    /** Hapus jurnal berdasarkan type + remarks (untuk unpost Note). Detail ter-cascade. */
    @Transactional
    public int deleteByTypeAndRemarks(String type, String remarks) {
        List<JournalHdr> list = repo.findByJournalTypeAndRemarks(type, remarks);
        if (list.isEmpty()) return 0;
        repo.deleteAll(list);
        return list.size();
    }

    /** Hapus jurnal milik tenant/company tertentu (untuk unpost Note). */
    @Transactional
    public int deleteByTypeAndRemarks(Long tenantId, Long companyId, String type, String remarks) {
        List<JournalHdr> list = repo.findByScopedTypeAndRemarks(tenantId, companyId, type, remarks);
        if (list.isEmpty()) return 0;
        repo.deleteAll(list);
        return list.size();
    }
}