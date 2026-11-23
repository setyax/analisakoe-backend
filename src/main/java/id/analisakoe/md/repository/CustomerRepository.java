package id.analisakoe.md.repository;

import id.analisakoe.md.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Page<Customer> findByTenantIdAndDeletedAtIsNull(Long tenantId, Pageable pageable);
    Page<Customer> findByTenantIdAndDeletedAtIsNullAndNameContainingIgnoreCase(Long tenantId, String name, Pageable pageable);
    Optional<Customer> findByIdAndTenantIdAndDeletedAtIsNull(Long id, Long tenantId);
    boolean existsByTenantIdAndCompanyIdAndCode(Long tenantId, Long companyId, String code);
}