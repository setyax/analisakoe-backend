package id.analisakoe.pur.repository;

import id.analisakoe.pur.entity.PurchasePolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PurchasePolicyRepository extends JpaRepository<PurchasePolicy, Long> {

    @Query("SELECT p FROM PurchasePolicy p " +
           "WHERE p.companyId = :companyId AND p.active = true " +
           "ORDER BY p.priority ASC")
    List<PurchasePolicy> findActiveByCompanyOrderByPriority(@Param("companyId") Long companyId);
}
