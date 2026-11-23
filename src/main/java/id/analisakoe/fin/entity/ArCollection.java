package id.analisakoe.fin.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
@Entity @Table(name = "ar_collection", schema = "fin")
public class ArCollection {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "tenant_id") private Long tenantId;
    @Column(name = "company_id") private Long companyId;
    @Column(name = "doc_no") private String docNo;
    @Column(name = "doc_date") private LocalDate docDate;
    @Column(name = "customer_id") private Long customerId;
    @Column(name = "bank_id") private Long bankId;
    private BigDecimal amount;
    private String remarks;
    private String status;
    
    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; } public void setTenantId(Long v) { this.tenantId = v; }
    public Long getCompanyId() { return companyId; } public void setCompanyId(Long v) { this.companyId = v; }
    public String getDocNo() { return docNo; } public void setDocNo(String v) { this.docNo = v; }
    public LocalDate getDocDate() { return docDate; } public void setDocDate(LocalDate v) { this.docDate = v; }
    public Long getCustomerId() { return customerId; } public void setCustomerId(Long v) { this.customerId = v; }
    public Long getBankId() { return bankId; } public void setBankId(Long v) { this.bankId = v; }
    public BigDecimal getAmount() { return amount; } public void setAmount(BigDecimal v) { this.amount = v; }
    public String getStatus() { return status; } public void setStatus(String v) { this.status = v; }
    public String getRemarks() { return remarks; } public void setRemarks(String v) { this.remarks = v; }
}