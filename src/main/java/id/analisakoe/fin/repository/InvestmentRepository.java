package id.analisakoe.fin.repository;
import id.analisakoe.fin.entity.Investment;
import org.springframework.data.jpa.repository.JpaRepository;
public interface InvestmentRepository extends JpaRepository<Investment, Long> {}