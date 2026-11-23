package id.analisakoe.fin.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity @Table(name = "bank_account", schema = "fin")
public class BankAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "tenant_id") private Long tenantId;
    @Column(name = "company_id") private Long companyId;
    private String name;
    @Column(name = "account_no") private String accountNo;
    private String status;
    @Column(name = "created_at") private OffsetDateTime createdAt;
    @Column(name = "coa_id") private Long coaId;
    private String branch;
    private String currency;
    @Column(name = "opening_balance") private BigDecimal openingBalance;
    @Column(name = "current_balance") private BigDecimal currentBalance;
    @Column(name = "bank_code") private String bankCode;

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; } public void setTenantId(Long v) { this.tenantId = v; }
    public Long getCompanyId() { return companyId; } public void setCompanyId(Long v) { this.companyId = v; }
    public String getName() { return name; } public void setName(String v) { this.name = v; }
    public String getAccountNo() { return accountNo; } public void setAccountNo(String v) { this.accountNo = v; }
    public String getStatus() { return status; } public void setStatus(String v) { this.status = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public Long getCoaId() { return coaId; } public void setCoaId(Long v) { this.coaId = v; }
    public String getBranch() { return branch; } public void setBranch(String v) { this.branch = v; }
    public String getCurrency() { return currency; } public void setCurrency(String v) { this.currency = v; }
    public BigDecimal getOpeningBalance() { return openingBalance; } public void setOpeningBalance(BigDecimal v) { this.openingBalance = v; }
    public BigDecimal getCurrentBalance() { return currentBalance; } public void setCurrentBalance(BigDecimal v) { this.currentBalance = v; }
    public String getBankCode() { return bankCode; } public void setBankCode(String v) { this.bankCode = v; }
}
