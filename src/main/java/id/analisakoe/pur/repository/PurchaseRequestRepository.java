package id.analisakoe.pur.repository;

import id.analisakoe.pur.entity.PurchaseRequestHdr;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseRequestRepository extends JpaRepository<PurchaseRequestHdr, Long> {
    long countByTenantIdAndStatus(Long tenantId, String status);
    Page<PurchaseRequestHdr> findByTenantIdOrderByIdDesc(Long tenantId, Pageable pageable);
    Page<PurchaseRequestHdr> findByRequisitionerIdOrderByIdDesc(Long requisitionerId, Pageable pageable);
    Page<PurchaseRequestHdr> findByTenantIdAndCompanyIdAndRequisitionerIdOrderByIdDesc(
            Long tenantId, Long companyId, Long requisitionerId, Pageable pageable);
    java.util.Optional<PurchaseRequestHdr> findByIdAndTenantIdAndCompanyId(
            Long id, Long tenantId, Long companyId);
}