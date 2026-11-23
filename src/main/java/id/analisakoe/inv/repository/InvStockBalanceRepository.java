package id.analisakoe.inv.repository;

import id.analisakoe.inv.entity.InvStockBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InvStockBalanceRepository extends JpaRepository<InvStockBalance, Long> {

    @Query("SELECT b FROM InvStockBalance b WHERE b.warehouseId = :whId AND b.itemId = :itemId")
    Optional<InvStockBalance> findByWarehouseAndItem(@Param("whId") Long whId, @Param("itemId") Long itemId);

    @Query("SELECT b FROM InvStockBalance b WHERE b.qtyOnHand > 0 ORDER BY b.itemId")
    List<InvStockBalance> findAllWithStock();
}