package id.analisakoe.pur.repository;

import id.analisakoe.pur.entity.PurchaseInvoiceHdr;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PurchaseInvoiceRepository extends JpaRepository<PurchaseInvoiceHdr, Long> {
    Page<PurchaseInvoiceHdr> findByTenantIdOrderByIdDesc(Long tenantId, Pageable pageable);
    List<PurchaseInvoiceHdr> findByGrnIdAndStatusNot(Long grnId, String status);
}