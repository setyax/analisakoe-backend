package id.analisakoe.pur.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
@Entity @Table(name = "purchase_invoice_hdr", schema = "pur")
public class PurchaseInvoiceHdr {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "tenant_id") private Long tenantId;
    @Column(name = "company_id") private Long companyId;
    @Column(name = "doc_no") private String docNo;
    @Column(name = "supplier_invoice_no") private String supplierInvoiceNo;
    @Column(name = "invoice_date") private LocalDate invoiceDate;
    @Column(name = "due_date") private LocalDate dueDate;
    @Column(name = "supplier_id") private Long supplierId;
    @Column(name = "grn_id") private Long grnId;
    @Column(name = "total_amount") private BigDecimal totalAmount;
    @Column(name = "tax_amount") private BigDecimal taxAmount;
    @Column(name = "tax_type") private String taxType;
    private String status;
    private String remarks;
    @Column(name = "created_at") private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "header", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<PurchaseInvoiceDtl> details = new ArrayList<>();

    private String currency;

    @PrePersist void onCreate() { if(createdAt == null) createdAt = OffsetDateTime.now(); }
    public void addDetail(PurchaseInvoiceDtl d) { details.add(d); d.setHeader(this); }

    public Long getId() { return id; }
    public String getDocNo() { return docNo; } public void setDocNo(String v) { this.docNo = v; }
    public String getSupplierInvoiceNo() { return supplierInvoiceNo; } public void setSupplierInvoiceNo(String v) { this.supplierInvoiceNo = v; }
    public LocalDate getInvoiceDate() { return invoiceDate; } public void setInvoiceDate(LocalDate v) { this.invoiceDate = v; }
    public LocalDate getDueDate() { return dueDate; } public void setDueDate(LocalDate v) { this.dueDate = v; }
    public Long getSupplierId() { return supplierId; } public void setSupplierId(Long v) { this.supplierId = v; }
    public Long getGrnId() { return grnId; } public void setGrnId(Long v) { this.grnId = v; }
    public BigDecimal getTotalAmount() { return totalAmount; } public void setTotalAmount(BigDecimal v) { this.totalAmount = v; }
    public String getStatus() { return status; } public void setStatus(String v) { this.status = v; }
    public String getRemarks() { return remarks; } public void setRemarks(String v) { this.remarks = v; }
    public Long getTenantId() { return tenantId; } public void setTenantId(Long v) { this.tenantId = v; }
    public Long getCompanyId() { return companyId; } public void setCompanyId(Long v) { this.companyId = v; }
    public List<PurchaseInvoiceDtl> getDetails() { return details; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { this.currency = v; }

    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal v) { this.taxAmount = v; }
    public String getTaxType() { return taxType; }
    public void setTaxType(String v) { this.taxType = v; }
}