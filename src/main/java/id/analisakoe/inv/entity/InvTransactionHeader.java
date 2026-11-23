package id.analisakoe.inv.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "transaction_header", schema = "inv")
public class InvTransactionHeader {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @Column(name = "company_id", nullable = false)
    private Long companyId;
    
    @Column(name = "doc_type", nullable = false, length = 10)
    private String docType; // IN, OUT, ADJ
    
    @Column(name = "doc_no", nullable = false, length = 50)
    private String docNo;
    
    @Column(name = "doc_date", nullable = false)
    private LocalDate docDate;
    
    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;
    
    private String remarks;
    
    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "header", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InvTransactionDetail> details = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (status == null) status = "POSTED";
    }

    // Getters and Setters
    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long v) { this.tenantId = v; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long v) { this.companyId = v; }
    public String getDocType() { return docType; }
    public void setDocType(String v) { this.docType = v; }
    public String getDocNo() { return docNo; }
    public void setDocNo(String v) { this.docNo = v; }
    public LocalDate getDocDate() { return docDate; }
    public void setDocDate(LocalDate v) { this.docDate = v; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long v) { this.warehouseId = v; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String v) { this.remarks = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public List<InvTransactionDetail> getDetails() { return details; }
    public void setDetails(List<InvTransactionDetail> v) { this.details = v; }
    public void addDetail(InvTransactionDetail d) { details.add(d); d.setHeader(this); }
}