package id.analisakoe.core.repository;

import id.analisakoe.core.entity.NumberRange;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface NumberRangeRepository extends JpaRepository<NumberRange, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT n FROM NumberRange n WHERE n.tenantId = :tenantId AND n.docType = :docType AND n.period = :period")
    Optional<NumberRange> findForUpdate(@Param("tenantId") Long tenantId, @Param("docType") String docType, @Param("period") String period);
}