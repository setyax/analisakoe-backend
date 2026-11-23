package id.analisakoe.fin.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity @Table(name = "asset_master", schema = "fin")
public class AssetMaster {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "tenant_id") private Long tenantId;
    @Column(name = "company_id") private Long companyId;
    @Column(name = "asset_code") private String assetCode;
    @Column(name = "asset_name") private String assetName;
    private String category;
    @Column(name = "acquisition_date") private LocalDate acquisitionDate;
    @Column(name = "acquisition_cost") private BigDecimal acquisitionCost;
    @Column(name = "useful_life_years") private Integer usefulLifeYears;
    @Column(name = "salvage_value") private BigDecimal salvageValue;
    @Column(name = "depreciation_method") private String depreciationMethod;
    @Column(name = "accumulated_depreciation") private BigDecimal accumulatedDepreciation;
    @Column(name = "book_value") private BigDecimal bookValue;
    private String status;

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; } public void setTenantId(Long v) { this.tenantId = v; }
    public Long getCompanyId() { return companyId; } public void setCompanyId(Long v) { this.companyId = v; }
    public String getAssetCode() { return assetCode; } public void setAssetCode(String v) { this.assetCode = v; }
    public String getAssetName() { return assetName; } public void setAssetName(String v) { this.assetName = v; }
    public String getCategory() { return category; } public void setCategory(String v) { this.category = v; }
    public LocalDate getAcquisitionDate() { return acquisitionDate; } public void setAcquisitionDate(LocalDate v) { this.acquisitionDate = v; }
    public BigDecimal getAcquisitionCost() { return acquisitionCost; } public void setAcquisitionCost(BigDecimal v) { this.acquisitionCost = v; }
    public Integer getUsefulLifeYears() { return usefulLifeYears; } public void setUsefulLifeYears(Integer v) { this.usefulLifeYears = v; }
    public BigDecimal getSalvageValue() { return salvageValue; } public void setSalvageValue(BigDecimal v) { this.salvageValue = v; }
    public String getDepreciationMethod() { return depreciationMethod; } public void setDepreciationMethod(String v) { this.depreciationMethod = v; }
    public BigDecimal getAccumulatedDepreciation() { return accumulatedDepreciation; } public void setAccumulatedDepreciation(BigDecimal v) { this.accumulatedDepreciation = v; }
    public BigDecimal getBookValue() { return bookValue; } public void setBookValue(BigDecimal v) { this.bookValue = v; }
    public String getStatus() { return status; } public void setStatus(String v) { this.status = v; }
}