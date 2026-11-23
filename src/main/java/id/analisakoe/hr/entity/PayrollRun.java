package id.analisakoe.hr.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
@Entity @Table(name = "payroll_run", schema = "hr")
public class PayrollRun {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "tenant_id") private Long tenantId;
    @Column(name = "company_id") private Long companyId;
    private String period;
    @Column(name = "total_gross") private BigDecimal totalGross;
    @Column(name = "total_deduction") private BigDecimal totalDeduction;
    @Column(name = "total_net") private BigDecimal totalNet;
    private String status;
    @Column(name = "created_at") private OffsetDateTime createdAt;
    
    @OneToMany(mappedBy = "payrollRun", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PayrollDtl> details = new ArrayList<>();

    @PrePersist void onCreate() { if(createdAt == null) createdAt = OffsetDateTime.now(); }
    public void addDetail(PayrollDtl d) { details.add(d); d.setPayrollRun(this); }

    public Long getId() { return id; }
    public String getPeriod() { return period; } public void setPeriod(String v) { this.period = v; }
    public BigDecimal getTotalGross() { return totalGross; } public void setTotalGross(BigDecimal v) { this.totalGross = v; }
    public BigDecimal getTotalDeduction() { return totalDeduction; } public void setTotalDeduction(BigDecimal v) { this.totalDeduction = v; }
    public BigDecimal getTotalNet() { return totalNet; } public void setTotalNet(BigDecimal v) { this.totalNet = v; }
    public String getStatus() { return status; } public void setStatus(String v) { this.status = v; }
    public Long getTenantId() { return tenantId; } public void setTenantId(Long v) { this.tenantId = v; }
    public Long getCompanyId() { return companyId; } public void setCompanyId(Long v) { this.companyId = v; }
    public List<PayrollDtl> getDetails() { return details; }
}