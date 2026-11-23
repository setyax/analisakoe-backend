package id.analisakoe.pur.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "purchase_order_hdr", schema = "pur")
public class PurchaseOrderHdr {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "doc_no")
    private String docNo;

    @Column(name = "doc_date")
    private LocalDate docDate;

    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "pr_id")
    private Long prId;

    @Column(name = "remarks")
    private String remarks;

    @Column(name = "status")
    private String status;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "currency")
    private String currency;

    @Column(name = "tax_type")
    private String taxType;

    @Column(name = "tax_amount")
    private BigDecimal taxAmount;

    @Column(name = "total_amount")
    private BigDecimal totalAmount;

    @OneToMany(mappedBy = "header", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<PurchaseOrderDtl> details = new ArrayList<>();

    // ===== Direct PO support (V28) =====
    public enum PoType { FROM_PR, DIRECT }

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @jakarta.persistence.Column(name = "po_type", nullable = false)
    private PoType poType = PoType.FROM_PR;

    @jakarta.persistence.Column(name = "bypass_reason", columnDefinition = "text")
    private String bypassReason;

    @jakarta.persistence.Column(name = "bypass_approved_by")
    private Long bypassApprovedBy;

    @jakarta.persistence.Column(name = "bypass_approved_at")
    private java.time.OffsetDateTime bypassApprovedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public void addDetail(PurchaseOrderDtl d) {
        details.add(d);
        d.setHeader(this);
    }

    public Long getId() { return id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long v) { this.tenantId = v; }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long v) { this.companyId = v; }

    public String getDocNo() { return docNo; }
    public void setDocNo(String v) { this.docNo = v; }

    public LocalDate getDocDate() { return docDate; }
    public void setDocDate(LocalDate v) { this.docDate = v; }

    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long v) { this.supplierId = v; }

    public Long getPrId() { return prId; }
    public void setPrId(Long v) { this.prId = v; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String v) { this.remarks = v; }

    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }

    public OffsetDateTime getCreatedAt() { return createdAt; }

    public String getCurrency() { return currency; }
    public void setCurrency(String v) { this.currency = v; }

    public String getTaxType() { return taxType; }
    public void setTaxType(String v) { this.taxType = v; }

    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal v) { this.taxAmount = v; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal v) { this.totalAmount = v; }

    public List<PurchaseOrderDtl> getDetails() { return details; }

    public PoType getPoType() { return poType; }
    public void setPoType(PoType v) { this.poType = v; }
    public String getBypassReason() { return bypassReason; }
    public void setBypassReason(String v) { this.bypassReason = v; }
    public Long getBypassApprovedBy() { return bypassApprovedBy; }
    public void setBypassApprovedBy(Long v) { this.bypassApprovedBy = v; }
    public java.time.OffsetDateTime getBypassApprovedAt() { return bypassApprovedAt; }
    public void setBypassApprovedAt(java.time.OffsetDateTime v) { this.bypassApprovedAt = v; }
}