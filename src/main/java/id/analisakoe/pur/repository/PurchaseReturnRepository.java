package id.analisakoe.pur.repository;
import id.analisakoe.pur.entity.PurchaseReturnHdr;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface PurchaseReturnRepository extends JpaRepository<PurchaseReturnHdr, Long> {
    List<PurchaseReturnHdr> findAllByOrderByDocDateDesc();
}