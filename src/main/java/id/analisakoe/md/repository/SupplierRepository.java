package id.analisakoe.md.repository;

import id.analisakoe.md.entity.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    Page<Supplier> findByTenantIdAndDeletedAtIsNull(Long tenantId, Pageable pageable);
    Page<Supplier> findByTenantIdAndDeletedAtIsNullAndNameContainingIgnoreCase(Long tenantId, String name, Pageable pageable);
    Optional<Supplier> findByIdAndTenantIdAndDeletedAtIsNull(Long id, Long tenantId);
    boolean existsByTenantIdAndCompanyIdAndCode(Long tenantId, Long companyId, String code);
}