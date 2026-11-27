package id.analisakoe.md.repository;

import id.analisakoe.md.entity.Item;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long> {
    Page<Item> findByTenantIdAndDeletedAtIsNull(Long tenantId, Pageable pageable);
    Page<Item> findByTenantIdAndDeletedAtIsNullAndNameContainingIgnoreCase(Long tenantId, String name, Pageable pageable);
    Optional<Item> findByIdAndTenantIdAndDeletedAtIsNull(Long id, Long tenantId);
    boolean existsByTenantIdAndCompanyIdAndCode(Long tenantId, Long companyId, String code);

    Page<Item> findByTenantIdAndCompanyIdAndDeletedAtIsNull(
            Long tenantId, Long companyId, Pageable pageable);

    Page<Item> findByTenantIdAndCompanyIdAndDeletedAtIsNullAndNameContainingIgnoreCase(
            Long tenantId, Long companyId, String name, Pageable pageable);

    Optional<Item> findByIdAndTenantIdAndCompanyIdAndDeletedAtIsNull(
            Long id, Long tenantId, Long companyId);
}