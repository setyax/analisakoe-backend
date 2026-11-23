package id.analisakoe.fin.repository;
import id.analisakoe.fin.entity.CashBankTrx;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface CashBankTrxRepository extends JpaRepository<CashBankTrx, Long> {
    List<CashBankTrx> findAllByOrderByDocDateDesc();
    List<CashBankTrx> findByRemarks(String remarks);
}