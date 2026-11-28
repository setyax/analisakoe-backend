package id.analisakoe.pur.repository;

import id.analisakoe.pur.entity.GrnHdr;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface GrnRepository extends JpaRepository<GrnHdr, Long> {
    Page<GrnHdr> findByTenantIdOrderByIdDesc(Long tenantId, Pageable pageable);
    Optional<GrnHdr> findByIdAndTenantIdAndCompanyId(Long id, Long tenantId, Long companyId);

    List<GrnHdr> findByPoId(Long poId);
}