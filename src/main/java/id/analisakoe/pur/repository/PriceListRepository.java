package id.analisakoe.pur.repository;

import id.analisakoe.pur.entity.PurPriceList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface PriceListRepository extends JpaRepository<PurPriceList, Long> {

    List<PurPriceList> findByTenantIdAndCompanyIdOrderByIdDesc(Long tenantId, Long companyId);

    /**
     * Determinasi harga ala SAP: condition record yang masih berlaku dan
     * scale (min_qty) terbesar yang masih <= qty permintaan.
     */
    @Query("SELECT p FROM PurPriceList p WHERE p.tenantId = :tenantId " +
           "AND p.companyId = :companyId " +
           "AND p.supplierId = :supId AND p.itemId = :itemId " +
           "AND p.uomId = :uomId " +
           "AND p.conditionClass = 'PRICE' " +
           "AND p.validFrom <= :date AND (p.validTo IS NULL OR p.validTo >= :date) " +
           "AND p.minQty <= :qty " +
           "ORDER BY p.minQty DESC")
    List<PurPriceList> findApplicablePrices(@Param("tenantId") Long tenantId,
                                             @Param("companyId") Long companyId,
                                             @Param("supId") Long supId,
                                             @Param("itemId") Long itemId,
                                             @Param("uomId") Long uomId,
                                             @Param("date") LocalDate date,
                                             @Param("qty") BigDecimal qty);
}
