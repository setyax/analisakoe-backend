package id.analisakoe.core.repository;

import id.analisakoe.core.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    List<Role> findByTenantId(Long tenantId);
    Optional<Role> findByTenantIdAndCode(Long tenantId, String code);
    List<Role> findByTenantIdAndCodeIn(Long tenantId, List<String> codes);
    boolean existsByTenantIdAndCode(Long tenantId, String code);
}