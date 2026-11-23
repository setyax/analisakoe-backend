package id.analisakoe.sal.repository;
import id.analisakoe.sal.entity.DeliveryOrderHdr;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DeliveryOrderRepository extends JpaRepository<DeliveryOrderHdr, Long> {
    List<DeliveryOrderHdr> findByTenantIdOrderByIdDesc(Long tenantId);
    Page<DeliveryOrderHdr> findByTenantIdOrderByIdDesc(Long tenantId, Pageable pageable);
}