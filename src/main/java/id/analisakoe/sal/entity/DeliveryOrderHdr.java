package id.analisakoe.sal.entity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
@Entity @Table(name = "delivery_order_hdr", schema = "sal")
public class DeliveryOrderHdr {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long tenantId;
    private Long companyId;
    private String docNo;
    private LocalDate docDate;
    private Long soId;
    private Long customerId;
    private String status;
    private String remarks;
    
    @OneToMany(mappedBy = "header", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<DeliveryOrderDtl> details = new ArrayList<>();
    
    public void addDetail(DeliveryOrderDtl d) { details.add(d); d.setHeader(this); }
    
    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; } public void setTenantId(Long v) { this.tenantId = v; }
    public Long getCompanyId() { return companyId; } public void setCompanyId(Long v) { this.companyId = v; }
    public String getDocNo() { return docNo; } public void setDocNo(String v) { this.docNo = v; }
    public LocalDate getDocDate() { return docDate; } public void setDocDate(LocalDate v) { this.docDate = v; }
    public Long getSoId() { return soId; } public void setSoId(Long v) { this.soId = v; }
    public Long getCustomerId() { return customerId; } public void setCustomerId(Long v) { this.customerId = v; }
    public String getStatus() { return status; } public void setStatus(String v) { this.status = v; }
    public String getRemarks() { return remarks; } public void setRemarks(String v) { this.remarks = v; }
    public List<DeliveryOrderDtl> getDetails() { return details; }
}