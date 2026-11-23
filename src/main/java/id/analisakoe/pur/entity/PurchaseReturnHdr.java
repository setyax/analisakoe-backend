package id.analisakoe.pur.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(name = "purchase_return_hdr", schema = "pur")
public class PurchaseReturnHdr {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "tenant_id") private Long tenantId;
    @Column(name = "company_id") private Long companyId;
    @Column(name = "doc_no") private String docNo;
    @Column(name = "doc_date") private LocalDate docDate;
    @Column(name = "supplier_id") private Long supplierId;
    @Column(name = "purchase_invoice_id") private Long purchaseInvoiceId;
    @Column(name = "warehouse_id") private Long warehouseId;
    @Column(name = "total_amount") private BigDecimal totalAmount;
    @Column(name = "tax_amount") private BigDecimal taxAmount;
    private String status;
    private String remarks;
    @Column(name = "created_at") private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "header", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseReturnDtl> details = new ArrayList<>();

    @PrePersist void onCreate() { if(createdAt == null) createdAt = OffsetDateTime.now(); }
    public void addDetail(PurchaseReturnDtl d) { details.add(d); d.setHeader(this); }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; } public void setTenantId(Long v) { this.tenantId = v; }
    public Long getCompanyId() { return companyId; } public void setCompanyId(Long v) { this.companyId = v; }
    public String getDocNo() { return docNo; } public void setDocNo(String v) { this.docNo = v; }
    public LocalDate getDocDate() { return docDate; } public void setDocDate(LocalDate v) { this.docDate = v; }
    public Long getSupplierId() { return supplierId; } public void setSupplierId(Long v) { this.supplierId = v; }
    public Long getPurchaseInvoiceId() { return purchaseInvoiceId; } public void setPurchaseInvoiceId(Long v) { this.purchaseInvoiceId = v; }
    public Long getWarehouseId() { return warehouseId; } public void setWarehouseId(Long v) { this.warehouseId = v; }
    public BigDecimal getTotalAmount() { return totalAmount; } public void setTotalAmount(BigDecimal v) { this.totalAmount = v; }
    public BigDecimal getTaxAmount() { return taxAmount; } public void setTaxAmount(BigDecimal v) { this.taxAmount = v; }
    public String getStatus() { return status; } public void setStatus(String v) { this.status = v; }
    public String getRemarks() { return remarks; } public void setRemarks(String v) { this.remarks = v; }
    public List<PurchaseReturnDtl> getDetails() { return details; }
}