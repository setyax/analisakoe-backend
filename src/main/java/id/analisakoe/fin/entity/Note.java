package id.analisakoe.fin.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity @Table(name = "notes", schema = "fin")
public class Note {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "tenant_id") private Long tenantId;
    @Column(name = "company_id") private Long companyId;
    @Column(name = "doc_no") private String docNo;
    @Column(name = "doc_date") private LocalDate docDate;
    @Column(name = "note_type") private String noteType;
    @Column(name = "partner_id") private Long partnerId;
    @Column(name = "ref_invoice_id") private Long refInvoiceId;
    @Column(name = "total_amount") private BigDecimal totalAmount;
    @Column(name = "tax_amount") private BigDecimal taxAmount;
    private String remarks;
    private String status;
    @Column(name = "created_at") private OffsetDateTime createdAt;

    @PrePersist void onCreate() { if(createdAt == null) createdAt = OffsetDateTime.now(); }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; } public void setTenantId(Long v) { this.tenantId = v; }
    public Long getCompanyId() { return companyId; } public void setCompanyId(Long v) { this.companyId = v; }
    public String getDocNo() { return docNo; } public void setDocNo(String v) { this.docNo = v; }
    public LocalDate getDocDate() { return docDate; } public void setDocDate(LocalDate v) { this.docDate = v; }
    public String getNoteType() { return noteType; } public void setNoteType(String v) { this.noteType = v; }
    public Long getPartnerId() { return partnerId; } public void setPartnerId(Long v) { this.partnerId = v; }
    public Long getRefInvoiceId() { return refInvoiceId; } public void setRefInvoiceId(Long v) { this.refInvoiceId = v; }
    public BigDecimal getTotalAmount() { return totalAmount; } public void setTotalAmount(BigDecimal v) { this.totalAmount = v; }
    public BigDecimal getTaxAmount() { return taxAmount; } public void setTaxAmount(BigDecimal v) { this.taxAmount = v; }
    public String getRemarks() { return remarks; } public void setRemarks(String v) { this.remarks = v; }
    public String getStatus() { return status; } public void setStatus(String v) { this.status = v; }
}