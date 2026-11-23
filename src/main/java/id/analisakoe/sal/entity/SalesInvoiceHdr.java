package id.analisakoe.sal.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
@Entity @Table(name = "sales_invoice_hdr", schema = "sal")
public class SalesInvoiceHdr {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long tenantId;
    private Long companyId;
    private String docNo;
    private LocalDate docDate;
    private LocalDate invoiceDate;
    private LocalDate dueDate;
    private Long doId;
    private Long customerId;
    private BigDecimal taxAmount;

    // TAX TYPE (added)
    private String taxType;
    private BigDecimal totalAmount;

    @Column(name = "salesperson_id")
    private Long salespersonId;
    private String status;
    private String remarks;
    private String coretaxStatus;
    private String nsfp;
    private String coretaxMessage;
    
    @OneToMany(mappedBy = "header", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<SalesInvoiceDtl> details = new ArrayList<>();
    
    public void addDetail(SalesInvoiceDtl d) { details.add(d); d.setHeader(this); }
    
    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; } public void setTenantId(Long v) { this.tenantId = v; }
    public Long getCompanyId() { return companyId; } public void setCompanyId(Long v) { this.companyId = v; }
    public String getDocNo() { return docNo; } public void setDocNo(String v) { this.docNo = v; }
    public LocalDate getDocDate() { return docDate; } public void setDocDate(LocalDate v) { this.docDate = v; }
    public LocalDate getInvoiceDate() { return invoiceDate; } public void setInvoiceDate(LocalDate v) { this.invoiceDate = v; }
    public LocalDate getDueDate() { return dueDate; } public void setDueDate(LocalDate v) { this.dueDate = v; }
    public Long getDoId() { return doId; } public void setDoId(Long v) { this.doId = v; }
    public Long getCustomerId() { return customerId; } public void setCustomerId(Long v) { this.customerId = v; }
    public BigDecimal getTaxAmount() { return taxAmount; } public void setTaxAmount(BigDecimal v) { this.taxAmount = v; }
    public BigDecimal getTotalAmount() { return totalAmount; } public void setTotalAmount(BigDecimal v) { this.totalAmount = v; }
    public Long getSalespersonId() { return salespersonId; } public void setSalespersonId(Long v) { this.salespersonId = v; }
    public String getStatus() { return status; } public void setStatus(String v) { this.status = v; }
    public String getRemarks() { return remarks; } public void setRemarks(String v) { this.remarks = v; }
    public String getCoretaxStatus() { return coretaxStatus; } public void setCoretaxStatus(String v) { this.coretaxStatus = v; }
    public String getNsfp() { return nsfp; } public void setNsfp(String v) { this.nsfp = v; }
    public String getCoretaxMessage() { return coretaxMessage; } public void setCoretaxMessage(String v) { this.coretaxMessage = v; }
    public List<SalesInvoiceDtl> getDetails() { return details; }


    // === TAX TYPE ACCESSORS (added) ===
    public String getTaxType() { return taxType; }
    public void setTaxType(String taxType) { this.taxType = taxType; }
    // === END TAX TYPE ===
}