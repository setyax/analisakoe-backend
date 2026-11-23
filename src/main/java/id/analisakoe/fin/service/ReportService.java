package id.analisakoe.fin.service;

import id.analisakoe.fin.dto.ReportLineDto;
import id.analisakoe.fin.entity.ChartOfAccount;
import id.analisakoe.fin.repository.ChartOfAccountRepository;
import id.analisakoe.fin.repository.JournalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReportService {
    private final JournalRepository journalRepo;
    private final ChartOfAccountRepository coaRepo;

    public ReportService(JournalRepository journalRepo, ChartOfAccountRepository coaRepo) {
        this.journalRepo = journalRepo;
        this.coaRepo = coaRepo;
    }

    @Transactional(readOnly = true)
    public List<ReportLineDto> generateReport(LocalDate start, LocalDate end) {
        List<Object[]> results = journalRepo.sumBalancesByDateRange(start, end);
        
        Map<Long, ChartOfAccount> coaMap = coaRepo.findAll().stream()
            .collect(Collectors.toMap(ChartOfAccount::getId, c -> c));

        List<ReportLineDto> report = new ArrayList<>();
        for (Object[] row : results) {
            Long accountId = (Long) row[0];
            BigDecimal debit = (BigDecimal) row[1];
            BigDecimal credit = (BigDecimal) row[2];
            
            ChartOfAccount coa = coaMap.get(accountId);
            if (coa != null) {
                // Hitung Balance (Normal Balance)
                BigDecimal balance = BigDecimal.ZERO;
                if ("ASSET".equals(coa.getAccountType()) || "EXPENSE".equals(coa.getAccountType())) {
                    balance = debit.subtract(credit);
                } else {
                    balance = credit.subtract(debit);
                }

                report.add(new ReportLineDto(
                    coa.getCode(), coa.getName(), coa.getAccountType(), debit, credit, balance
                ));
            }
        }
        
        // Urutkan berdasarkan Kode Akun
        report.sort((a, b) -> a.accountCode().compareTo(b.accountCode()));
        return report;
    }
}