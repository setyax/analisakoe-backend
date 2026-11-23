package id.analisakoe.sal.repository;
import id.analisakoe.sal.entity.SalesInvoiceHdr;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SalesInvoiceRepository extends JpaRepository<SalesInvoiceHdr, Long> {
    List<SalesInvoiceHdr> findByTenantIdOrderByIdDesc(Long tenantId);
    Page<SalesInvoiceHdr> findByTenantIdOrderByIdDesc(Long tenantId, Pageable pageable);
}