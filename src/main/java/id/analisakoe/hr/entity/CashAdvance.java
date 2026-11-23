package id.analisakoe.hr.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity @Table(name = "cash_advance", schema = "hr")
public class CashAdvance {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "tenant_id") private Long tenantId;
    @Column(name = "company_id") private Long companyId;
    @Column(name = "doc_no") private String docNo;
    @Column(name = "doc_date") private LocalDate docDate;
    @Column(name = "employee_id") private Long employeeId;
    @Column(name = "bank_id") private Long bankId;
    private BigDecimal amount;
    private String remarks;
    private String status;
    @Column(name = "expense_amount") private BigDecimal expenseAmount;
    @Column(name = "return_amount") private BigDecimal returnAmount;
    @Column(name = "settlement_date") private LocalDate settlementDate;
    @Column(name = "created_at") private OffsetDateTime createdAt;

    @PrePersist void onCreate() { if(createdAt == null) createdAt = OffsetDateTime.now(); }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; } public void setTenantId(Long v) { this.tenantId = v; }
    public Long getCompanyId() { return companyId; } public void setCompanyId(Long v) { this.companyId = v; }
    public String getDocNo() { return docNo; } public void setDocNo(String v) { this.docNo = v; }
    public LocalDate getDocDate() { return docDate; } public void setDocDate(LocalDate v) { this.docDate = v; }
    public Long getEmployeeId() { return employeeId; } public void setEmployeeId(Long v) { this.employeeId = v; }
    public Long getBankId() { return bankId; } public void setBankId(Long v) { this.bankId = v; }
    public BigDecimal getAmount() { return amount; } public void setAmount(BigDecimal v) { this.amount = v; }
    public String getRemarks() { return remarks; } public void setRemarks(String v) { this.remarks = v; }
    public BigDecimal getExpenseAmount() { return expenseAmount; } public void setExpenseAmount(BigDecimal v) { this.expenseAmount = v; }
    public BigDecimal getReturnAmount() { return returnAmount; } public void setReturnAmount(BigDecimal v) { this.returnAmount = v; }
    public LocalDate getSettlementDate() { return settlementDate; } public void setSettlementDate(LocalDate v) { this.settlementDate = v; }
    public String getStatus() { return status; } public void setStatus(String v) { this.status = v; }
}