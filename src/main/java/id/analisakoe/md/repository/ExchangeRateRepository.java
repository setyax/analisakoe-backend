package id.analisakoe.md.repository;

import id.analisakoe.md.entity.ExchangeRate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {
    Page<ExchangeRate> findByTenantIdOrderByEffectiveDateDesc(Long tenantId, Pageable pageable);

    @Query("SELECT x FROM ExchangeRate x WHERE x.tenantId = :tenantId AND x.fromCurrency = :from AND x.toCurrency = :to AND x.effectiveDate <= :date ORDER BY x.effectiveDate DESC LIMIT 1")
    Optional<ExchangeRate> findLatestRate(@Param("tenantId") Long tenantId, @Param("from") String from, @Param("to") String to, @Param("date") LocalDate date);
}