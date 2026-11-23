package id.analisakoe.fin.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity @Table(name = "investment", schema = "fin")
public class Investment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "tenant_id") private Long tenantId;
    @Column(name = "company_id") private Long companyId;
    private String code;
    private String name;
    @Column(name = "acquisition_date") private LocalDate acquisitionDate;
    @Column(name = "book_value") private BigDecimal bookValue;
    @Column(name = "last_fair_value") private BigDecimal lastFairValue;
    private String status;

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public BigDecimal getBookValue() { return bookValue; } public void setBookValue(BigDecimal v) { this.bookValue = v; }
    public BigDecimal getLastFairValue() { return lastFairValue; } public void setLastFairValue(BigDecimal v) { this.lastFairValue = v; }
    public Long getTenantId() { return tenantId; } public void setTenantId(Long v) { this.tenantId = v; }
    public Long getCompanyId() { return companyId; } public void setCompanyId(Long v) { this.companyId = v; }
}