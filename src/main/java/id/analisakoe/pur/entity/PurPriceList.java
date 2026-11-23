package id.analisakoe.pur.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "price_list", schema = "pur")
public class PurPriceList {

    @jakarta.persistence.Column(name = "uom_id")
    private Long uomId;
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "tenant_id") private Long tenantId;
    @Column(name = "supplier_id") private Long supplierId;
    @Column(name = "item_id") private Long itemId;
    private BigDecimal price;
    private String currency;
    @Column(name = "valid_from") private LocalDate validFrom;
    @Column(name = "valid_to") private LocalDate validTo;
    @Column(name = "min_qty") private BigDecimal minQty;
    @Column(name = "created_at") private OffsetDateTime createdAt;
    @Column(name = "updated_at") private OffsetDateTime updatedAt;

    @PrePersist void onCreate() { createdAt = OffsetDateTime.now(); updatedAt = OffsetDateTime.now(); }
    @PreUpdate void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public Long getId() { return id; } public void setId(Long v) { this.id = v; }
    public Long getTenantId() { return tenantId; } public void setTenantId(Long v) { this.tenantId = v; }
    public Long getSupplierId() { return supplierId; } public void setSupplierId(Long v) { this.supplierId = v; }
    public Long getItemId() { return itemId; } public void setItemId(Long v) { this.itemId = v; }
    public BigDecimal getPrice() { return price; } public void setPrice(BigDecimal v) { this.price = v; }
    public String getCurrency() { return currency; } public void setCurrency(String v) { this.currency = v; }
    public LocalDate getValidFrom() { return validFrom; } public void setValidFrom(LocalDate v) { this.validFrom = v; }
    public LocalDate getValidTo() { return validTo; } public void setValidTo(LocalDate v) { this.validTo = v; }
    public BigDecimal getMinQty() { return minQty; } public void setMinQty(BigDecimal v) { this.minQty = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public Long getUomId() { return uomId; }
    public void setUomId(Long uomId) { this.uomId = uomId; }
}