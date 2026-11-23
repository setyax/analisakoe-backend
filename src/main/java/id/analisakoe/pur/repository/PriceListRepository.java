package id.analisakoe.pur.repository;

import id.analisakoe.pur.entity.PurPriceList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;

public interface PriceListRepository extends JpaRepository<PurPriceList, Long> {
    List<PurPriceList> findByTenantIdOrderByIdDesc(Long tenantId);

    @Query("SELECT p FROM PurPriceList p WHERE p.supplierId = :supId AND p.itemId = :itemId " +
           "AND p.validFrom <= :date AND (p.validTo IS NULL OR p.validTo >= :date) " +
           "ORDER BY p.minQty DESC")
    List<PurPriceList> findApplicablePrices(@Param("supId") Long supId,
                                             @Param("itemId") Long itemId,
                                             @Param("date") LocalDate date);
}