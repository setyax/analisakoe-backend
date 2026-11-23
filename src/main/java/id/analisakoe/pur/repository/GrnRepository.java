package id.analisakoe.pur.repository;

import id.analisakoe.pur.entity.GrnHdr;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GrnRepository extends JpaRepository<GrnHdr, Long> {
    Page<GrnHdr> findByTenantIdOrderByIdDesc(Long tenantId, Pageable pageable);

    List<GrnHdr> findByPoId(Long poId);
}