package id.analisakoe.md.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "item", schema = "md")
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    private String description;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "base_uom_id", nullable = false)
    private Long baseUomId;

    @Column(name = "item_type", nullable = false, length = 16)
    private String itemType;

    @Column(name = "track_lot", nullable = false)
    private Boolean trackLot;

    @Column(name = "track_serial", nullable = false)
    private Boolean trackSerial;

    private String barcode;
    
    @Column(name = "purchasing_group", length = 20)
    private String purchasingGroup;

    @Column(name = "lead_time_days")
    private Integer leadTimeDays;

    @Column(name = "price_control", length = 1)
    private String priceControl;

    @Column(name = "valuation_class", length = 20)
    private String valuationClass;

    @Column(name = "standard_price")
    private java.math.BigDecimal standardPrice;
    @Column(name = "djp_goods_code", length = 10) private String djpGoodsCode;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long v) { this.tenantId = v; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long v) { this.companyId = v; }
    public String getCode() { return code; }
    public void setCode(String v) { this.code = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long v) { this.categoryId = v; }
    public Long getBaseUomId() { return baseUomId; }
    public void setBaseUomId(Long v) { this.baseUomId = v; }
    public String getItemType() { return itemType; }
    public void setItemType(String v) { this.itemType = v; }
    public Boolean getTrackLot() { return trackLot; }
    public void setTrackLot(Boolean v) { this.trackLot = v; }
    public Boolean getTrackSerial() { return trackSerial; }
    public void setTrackSerial(Boolean v) { this.trackSerial = v; }
    public String getPurchasingGroup() { return purchasingGroup; } public void setPurchasingGroup(String v) { this.purchasingGroup = v; }
    public Integer getLeadTimeDays() { return leadTimeDays; } public void setLeadTimeDays(Integer v) { this.leadTimeDays = v; }
    public String getPriceControl() { return priceControl; } public void setPriceControl(String v) { this.priceControl = v; }
    public String getValuationClass() { return valuationClass; } public void setValuationClass(String v) { this.valuationClass = v; }
    public String getDjpGoodsCode() { return djpGoodsCode; } public void setDjpGoodsCode(String v) { this.djpGoodsCode = v; }
    public java.math.BigDecimal getStandardPrice() { return standardPrice; } public void setStandardPrice(java.math.BigDecimal v) { this.standardPrice = v; }
    public String getBarcode() { return barcode; }
    public void setBarcode(String v) { this.barcode = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(OffsetDateTime v) { this.deletedAt = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}