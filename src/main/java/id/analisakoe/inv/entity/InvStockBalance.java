package id.analisakoe.inv.entity;

import id.analisakoe.context.ContextHolder;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "stock_balance", schema = "inv")
public class InvStockBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    // Field Java tetap "qtyOnHand" (agar kode lama tidak perlu diubah)
    // tapi map ke kolom baru "qty"
    @Column(name = "qty", nullable = false)
    private BigDecimal qtyOnHand;

    @Column(name = "avg_cost", nullable = false)
    private BigDecimal avgCost = BigDecimal.ZERO;

    @Column(name = "total_value", nullable = false)
    private BigDecimal totalValue = BigDecimal.ZERO;

    @Column(name = "last_updated", nullable = false)
    private OffsetDateTime lastUpdated;

    @PrePersist
    @PreUpdate
    void onUpdate() {
        lastUpdated = OffsetDateTime.now();
        if (qtyOnHand == null) qtyOnHand = BigDecimal.ZERO;
        if (avgCost == null) avgCost = BigDecimal.ZERO;
        if (totalValue == null) totalValue = BigDecimal.ZERO;
        if (tenantId == null) {
            var ctx = ContextHolder.get();
            tenantId = ctx == null ? null : ctx.tenantId();
        }
    }

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long v) { this.tenantId = v; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long v) { this.warehouseId = v; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long v) { this.itemId = v; }
    public BigDecimal getQtyOnHand() { return qtyOnHand; }
    public void setQtyOnHand(BigDecimal v) { this.qtyOnHand = v; }
    public BigDecimal getAvgCost() { return avgCost; }
    public void setAvgCost(BigDecimal v) { this.avgCost = v; }
    public BigDecimal getTotalValue() { return totalValue; }
    public void setTotalValue(BigDecimal v) { this.totalValue = v; }
    public OffsetDateTime getLastUpdated() { return lastUpdated; }
}