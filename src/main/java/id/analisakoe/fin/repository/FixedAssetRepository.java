package id.analisakoe.fin.repository;
import id.analisakoe.fin.entity.FixedAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FixedAssetRepository extends JpaRepository<FixedAsset, Long> {
    List<FixedAsset> findByTenantIdOrderByAssetCodeAsc(Long tenantId);
}