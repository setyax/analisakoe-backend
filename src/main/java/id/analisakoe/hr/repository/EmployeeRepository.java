package id.analisakoe.hr.repository;
import id.analisakoe.hr.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    List<Employee> findByTenantIdAndStatus(Long tenantId, String status);
    List<Employee> findByTenantIdAndCompanyIdOrderByIdDesc(Long tenantId, Long companyId);
    Optional<Employee> findByIdAndTenantIdAndCompanyId(Long id, Long tenantId, Long companyId);
}
