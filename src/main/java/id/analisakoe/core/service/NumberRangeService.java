package id.analisakoe.core.service;

import id.analisakoe.context.ContextService;
import id.analisakoe.core.entity.NumberRange;
import id.analisakoe.core.repository.NumberRangeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

@Service
public class NumberRangeService {
    private final NumberRangeRepository repo;
    private final ContextService context;

    public NumberRangeService(NumberRangeRepository repo, ContextService context) {
        this.repo = repo;
        this.context = context;
    }

    @Transactional
    public String generate(String docType, String prefix) {
        String period = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        
        Long tenantId = context.tenantId();
        NumberRange nr = repo.findForUpdate(tenantId, docType, period)
            .orElseGet(() -> repo.save(new NumberRange(tenantId, docType, prefix, period, 0)));

        nr.setCurrentNumber(nr.getCurrentNumber() + 1);
        repo.save(nr);

        String year = period.substring(0, 4);
        String month = period.substring(4, 6);
        return String.format("%s/%s/%s/%04d", prefix, year, month, nr.getCurrentNumber());
    }
}