package id.analisakoe.md.repository;

import id.analisakoe.md.entity.Warehouse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
    Page<Warehouse> findByTenantIdAndNameContainingIgnoreCase(Long tenantId, String name, Pageable pageable);
    Page<Warehouse> findByTenantId(Long tenantId, Pageable pageable);
    Optional<Warehouse> findByIdAndTenantId(Long id, Long tenantId);
    boolean existsByTenantIdAndCode(Long tenantId, String code);
    boolean existsByTenantIdAndCompanyIdAndCode(Long tenantId, Long companyId, String code);

    Page<Warehouse> findByTenantIdAndCompanyId(
            Long tenantId, Long companyId, Pageable pageable);

    Page<Warehouse> findByTenantIdAndCompanyIdAndNameContainingIgnoreCase(
            Long tenantId, Long companyId, String name, Pageable pageable);

    Optional<Warehouse> findByIdAndTenantIdAndCompanyId(
            Long id, Long tenantId, Long companyId);
}