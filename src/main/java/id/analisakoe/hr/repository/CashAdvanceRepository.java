package id.analisakoe.hr.repository;

import id.analisakoe.hr.entity.CashAdvance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CashAdvanceRepository extends JpaRepository<CashAdvance, Long> {
    
    // Method lama (biarkan jika masih dipakai di tempat lain)
    List<CashAdvance> findAllByOrderByDocDateDesc();

    // TAMBAHKAN METHOD INI:
    List<CashAdvance> findByCompanyIdOrderByDocDateDesc(Long companyId);
}