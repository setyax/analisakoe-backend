package id.analisakoe.hr.repository;
import id.analisakoe.hr.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    List<Employee> findByTenantIdAndStatus(Long tenantId, String status);
}