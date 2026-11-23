package id.analisakoe.core.repository;

import id.analisakoe.core.entity.AppUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);
    Optional<AppUser> findByEmail(String email);
    Optional<AppUser> findByIdAndTenantId(Long id, Long tenantId);
    boolean existsByTenantIdAndUsername(Long tenantId, String username);

    Page<AppUser> findByTenantId(Long tenantId, Pageable pageable);
    Page<AppUser> findByTenantIdAndUsernameContainingIgnoreCase(Long tenantId, String username, Pageable pageable);
    Page<AppUser> findByTenantIdAndStatus(Long tenantId, String status, Pageable pageable);
}