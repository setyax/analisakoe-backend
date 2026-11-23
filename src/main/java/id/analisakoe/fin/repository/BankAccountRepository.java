package id.analisakoe.fin.repository;
import id.analisakoe.fin.entity.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {
    List<BankAccount> findAllByOrderByNameAsc();
    List<BankAccount> findByCompanyIdOrderByNameAsc(Long companyId);
}
