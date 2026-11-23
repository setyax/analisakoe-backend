package id.analisakoe.pur.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(name = "purchase_request_hdr", schema = "pur")
public class PurchaseRequestHdr {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "tenant_id") private Long tenantId;
    @Column(name = "company_id") private Long companyId;
    @Column(name = "doc_no") private String docNo;
    @Column(name = "doc_date") private LocalDate docDate;
    private String remarks;
    private String status;
    @Column(name = "doc_type") private String docType;
    @Column(name = "purchasing_org") private String purchasingOrg;
    @Column(name = "purchasing_group") private String purchasingGroup;
    @Column(name = "requisitioner_id") private Long requisitionerId;
    @Column(name = "created_at") private OffsetDateTime createdAt;
    
    @OneToMany(mappedBy = "header", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<PurchaseRequestDtl> details = new ArrayList<>();

    private String currency;

    @PrePersist void onCreate() { if(createdAt == null) createdAt = OffsetDateTime.now(); }
    public void addDetail(PurchaseRequestDtl dtl) { details.add(dtl); dtl.setHeader(this); }

    public Long getId() { return id; }
    public String getDocNo() { return docNo; } public void setDocNo(String v) { this.docNo = v; }
    public LocalDate getDocDate() { return docDate; } public void setDocDate(LocalDate v) { this.docDate = v; }
    public String getRemarks() { return remarks; } public void setRemarks(String v) { this.remarks = v; }
    public String getDocType() { return docType; } public void setDocType(String v) { this.docType = v; }
    public String getPurchasingOrg() { return purchasingOrg; } public void setPurchasingOrg(String v) { this.purchasingOrg = v; }
    public String getPurchasingGroup() { return purchasingGroup; } public void setPurchasingGroup(String v) { this.purchasingGroup = v; }
    public Long getRequisitionerId() { return requisitionerId; } public void setRequisitionerId(Long v) { this.requisitionerId = v; }
    public String getStatus() { return status; } public void setStatus(String v) { this.status = v; }
    public Long getTenantId() { return tenantId; } public void setTenantId(Long v) { this.tenantId = v; }
    public Long getCompanyId() { return companyId; } public void setCompanyId(Long v) { this.companyId = v; }
    public List<PurchaseRequestDtl> getDetails() { return details; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { this.currency = v; }
}
