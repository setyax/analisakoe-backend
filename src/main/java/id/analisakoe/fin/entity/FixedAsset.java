package id.analisakoe.fin.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity @Table(name = "fixed_asset", schema = "fin")
public class FixedAsset {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long tenantId;
    private Long companyId;
    private String assetCode;
    private String assetName;
    private LocalDate acquisitionDate;
    private BigDecimal acquisitionCost;
    private Integer usefulLifeMonths;
    private BigDecimal salvageValue = BigDecimal.ZERO;
    private String status = "ACTIVE";
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @PrePersist void onCreate() { createdAt = OffsetDateTime.now(); updatedAt = OffsetDateTime.now(); }
    @PreUpdate void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; } public void setTenantId(Long v) { this.tenantId = v; }
    public Long getCompanyId() { return companyId; } public void setCompanyId(Long v) { this.companyId = v; }
    public String getAssetCode() { return assetCode; } public void setAssetCode(String v) { this.assetCode = v; }
    public String getAssetName() { return assetName; } public void setAssetName(String v) { this.assetName = v; }
    public LocalDate getAcquisitionDate() { return acquisitionDate; } public void setAcquisitionDate(LocalDate v) { this.acquisitionDate = v; }
    public BigDecimal getAcquisitionCost() { return acquisitionCost; } public void setAcquisitionCost(BigDecimal v) { this.acquisitionCost = v; }
    public Integer getUsefulLifeMonths() { return usefulLifeMonths; } public void setUsefulLifeMonths(Integer v) { this.usefulLifeMonths = v; }
    public BigDecimal getSalvageValue() { return salvageValue; } public void setSalvageValue(BigDecimal v) { this.salvageValue = v; }
    public String getStatus() { return status; } public void setStatus(String v) { this.status = v; }
}