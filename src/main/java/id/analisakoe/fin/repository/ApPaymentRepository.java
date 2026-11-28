package id.analisakoe.fin.repository;
import id.analisakoe.fin.entity.ApPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ApPaymentRepository extends JpaRepository<ApPayment, Long> {
    List<ApPayment> findAllByOrderByDocDateDesc();
    List<ApPayment> findByCompanyIdOrderByDocDateDesc(Long companyId);
}