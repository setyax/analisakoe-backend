package id.analisakoe.hr.repository;

import id.analisakoe.hr.entity.PayrollRun;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PayrollRunRepository extends JpaRepository<PayrollRun, Long> {
    
    @EntityGraph(attributePaths = {"details"}) // <-- TAMBAHKAN INI
    List<PayrollRun> findAll();

    @EntityGraph(attributePaths = {"details"}) // <-- TAMBAHKAN INI
    Optional<PayrollRun> findById(Long id);

    Optional<PayrollRun> findByPeriod(String period);
}