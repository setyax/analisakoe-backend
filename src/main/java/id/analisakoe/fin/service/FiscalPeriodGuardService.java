package id.analisakoe.fin.service;

import id.analisakoe.common.BusinessException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.YearMonth;

/** Central posting gate. A period must exist and be OPEN for the exact company. */
@Service
public class FiscalPeriodGuardService {
    @PersistenceContext private EntityManager em;

    @Transactional(readOnly = true)
    public void assertOpen(Long tenantId, Long companyId, LocalDate documentDate, String module, String documentNo) {
        if (tenantId == null || companyId == null || documentDate == null)
            throw new BusinessException("Fiscal Period tidak dapat divalidasi: tenant, company, atau tanggal dokumen kosong.");
        String period = YearMonth.from(documentDate).toString();
        var rows = em.createNativeQuery("SELECT status FROM fin.fiscal_period WHERE tenant_id=:tenant AND company_id=:company AND period=:period")
            .setParameter("tenant", tenantId).setParameter("company", companyId).setParameter("period", period).getResultList();
        if (rows.isEmpty()) throw new BusinessException("Fiscal Period " + period + " belum dibuka untuk company " + companyId + ". Posting " + module + " ditolak.");
        String status = String.valueOf(rows.get(0));
        if (!"OPEN".equalsIgnoreCase(status)) throw new BusinessException("Fiscal Period " + period + " berstatus " + status + ". Posting " + module + " " + (documentNo == null ? "" : documentNo) + " ditolak.");
    }
}
