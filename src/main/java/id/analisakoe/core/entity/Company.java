package id.analisakoe.core.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "company", schema = "core")
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "tax_id", length = 50)
    private String taxId;

    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency;
    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;
    @Column(name = "local_currency", nullable = false, length = 3)
    private String localCurrency;
    @Column(name = "chart_of_accounts_id")
    private Long chartOfAccountsId;

    @Column(nullable = false, length = 50)
    private String timezone;

    @Column(name = "fiscal_year_start_month", nullable = false)
    private Short fiscalYearStartMonth;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long v) { this.tenantId = v; }
    public String getCode() { return code; }
    public void setCode(String v) { this.code = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getTaxId() { return taxId; }
    public void setTaxId(String v) { this.taxId = v; }
    public String getBaseCurrency() { return baseCurrency; }
    public void setBaseCurrency(String v) { this.baseCurrency = v; }
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String v) { this.countryCode = v; }
    public String getLocalCurrency() { return localCurrency; }
    public void setLocalCurrency(String v) { this.localCurrency = v; }
    public Long getChartOfAccountsId() { return chartOfAccountsId; }
    public void setChartOfAccountsId(Long v) { this.chartOfAccountsId = v; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String v) { this.timezone = v; }
    public Short getFiscalYearStartMonth() { return fiscalYearStartMonth; }
    public void setFiscalYearStartMonth(Short v) { this.fiscalYearStartMonth = v; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long v) { this.parentId = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (updatedAt == null) updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}