package id.analisakoe.md.repository;

import id.analisakoe.md.entity.Uom;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UomRepository extends JpaRepository<Uom, Long> {
    Page<Uom> findByTenantIdAndNameContainingIgnoreCase(Long tenantId, String name, Pageable pageable);
    Page<Uom> findByTenantId(Long tenantId, Pageable pageable);
    Optional<Uom> findByIdAndTenantId(Long id, Long tenantId);
    boolean existsByTenantIdAndCode(Long tenantId, String code);
}