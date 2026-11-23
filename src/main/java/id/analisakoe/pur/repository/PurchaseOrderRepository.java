package id.analisakoe.pur.repository;

import id.analisakoe.pur.entity.PurchaseOrderHdr;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrderHdr, Long> {
    long countByTenantIdAndStatus(Long tenantId, String status);
    Page<PurchaseOrderHdr> findByTenantIdOrderByIdDesc(Long tenantId, Pageable pageable);
}