package id.analisakoe.fin.repository;

import id.analisakoe.fin.entity.ChartOfAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ChartOfAccountRepository extends JpaRepository<ChartOfAccount, Long> {
    Page<ChartOfAccount> findByTenantIdAndNameContainingIgnoreCase(Long tenantId, String name, Pageable pageable);
    Page<ChartOfAccount> findByTenantId(Long tenantId, Pageable pageable);
    Optional<ChartOfAccount> findByIdAndTenantId(Long id, Long tenantId);
    List<ChartOfAccount> findByTenantIdAndIsHeaderTrue(Long tenantId);
    boolean existsByTenantIdAndCompanyIdAndCode(Long tenantId, Long companyId, String code);
    java.util.Optional<ChartOfAccount> findByCode(String code);
}