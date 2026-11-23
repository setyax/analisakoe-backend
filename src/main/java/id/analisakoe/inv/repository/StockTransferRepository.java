package id.analisakoe.inv.repository;
import id.analisakoe.inv.entity.StockTransferHdr;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StockTransferRepository extends JpaRepository<StockTransferHdr, Long> {
    List<StockTransferHdr> findByTenantIdOrderByIdDesc(Long tenantId);
}