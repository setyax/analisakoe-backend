package id.analisakoe.inv.repository;

import id.analisakoe.inv.entity.InvTransactionHeader;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvTransactionRepository extends JpaRepository<InvTransactionHeader, Long> {
    Page<InvTransactionHeader> findByTenantIdOrderByIdDesc(Long tenantId, Pageable pageable);
}