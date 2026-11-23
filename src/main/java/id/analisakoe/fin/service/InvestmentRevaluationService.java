package id.analisakoe.fin.service;

import id.analisakoe.common.BusinessException;
import id.analisakoe.fin.entity.Investment;
import id.analisakoe.fin.repository.InvestmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class InvestmentRevaluationService {
    private final InvestmentRepository repo;
    private final JournalService journalService;

    public InvestmentRevaluationService(InvestmentRepository repo, JournalService journalService) {
        this.repo = repo; this.journalService = journalService;
    }

    @Transactional
    public void revaluate(Long investmentId, BigDecimal newFairValue, LocalDate revalDate) {
        Investment inv = repo.findById(investmentId).orElseThrow(() -> new BusinessException("Investasi tidak ditemukan"));
        
        BigDecimal bookValue = inv.getBookValue();
        BigDecimal difference = newFairValue.subtract(bookValue);

        if (difference.compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessException("Nilai wajar baru sama dengan nilai tercatat. Tidak ada jurnal yang dibuat.");
        }

        // LOGIKA PSAK 113 - FVTPL
        if (difference.compareTo(BigDecimal.ZERO) > 0) {
            // UNTUNG (GAIN): Nilai Wajar > Book Value
            // Jurnal: Debit Investasi (1104), Kredit Keuntungan Selisih FV (8101)
            journalService.createJournal(inv.getTenantId(), inv.getCompanyId(), revalDate, "FVTPL_GAIN", "Revaluasi Investasi " + inv.getCode(), List.of(
                new JournalService.JournalLine("1104", difference, BigDecimal.ZERO, "Penambahan Nilai Investasi"),
                new JournalService.JournalLine("8101", BigDecimal.ZERO, difference, "Keuntungan Selisih Nilai Wajar")
            ));
        } else {
            // RUGI (LOSS): Nilai Wajar < Book Value
            // Jurnal: Debit Kerugian Selisih FV (8102), Kredit Investasi (1104)
            BigDecimal lossAmount = difference.abs();
            journalService.createJournal(inv.getTenantId(), inv.getCompanyId(), revalDate, "FVTPL_LOSS", "Revaluasi Investasi " + inv.getCode(), List.of(
                new JournalService.JournalLine("8102", lossAmount, BigDecimal.ZERO, "Kerugian Selisih Nilai Wajar"),
                new JournalService.JournalLine("1104", BigDecimal.ZERO, lossAmount, "Penurunan Nilai Investasi")
            ));
        }

        // Update Nilai di Master Investasi
        inv.setBookValue(newFairValue);
        inv.setLastFairValue(newFairValue);
        repo.save(inv);
    }
}