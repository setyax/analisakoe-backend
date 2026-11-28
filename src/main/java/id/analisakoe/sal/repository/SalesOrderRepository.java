package id.analisakoe.sal.repository;

import id.analisakoe.sal.entity.SalesOrderHdr;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SalesOrderRepository extends JpaRepository<SalesOrderHdr, Long> {
    long countByTenantIdAndStatus(Long tenantId, String status);
    Page<SalesOrderHdr> findByTenantIdOrderByIdDesc(Long tenantId, Pageable pageable);
    Page<SalesOrderHdr> findByTenantIdAndCompanyIdOrderByIdDesc(Long tenantId, Long companyId, Pageable pageable);
    Optional<SalesOrderHdr> findByIdAndTenantIdAndCompanyId(Long id, Long tenantId, Long companyId);
    
    // BARU: filter by tenant + status
    List<SalesOrderHdr> findByTenantIdAndStatus(Long tenantId, String status);
    
    // BARU: filter by status saja (untuk dropdown)
    List<SalesOrderHdr> findByTenantIdAndStatusOrderByIdDesc(Long tenantId, String status);
}