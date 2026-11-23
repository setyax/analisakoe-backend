package id.analisakoe.inv.entity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(name = "stock_transfer_hdr", schema = "inv")
public class StockTransferHdr {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long tenantId;
    private String docNo;
    private LocalDate transferDate;
    private Long fromWarehouseId;
    private Long toWarehouseId;
    private String status;
    private String remarks;

    @OneToMany(mappedBy = "header", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<StockTransferDtl> details = new ArrayList<>();

    public void addDetail(StockTransferDtl d) { details.add(d); d.setHeader(this); }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; } public void setTenantId(Long v) { this.tenantId = v; }
    public String getDocNo() { return docNo; } public void setDocNo(String v) { this.docNo = v; }
    public LocalDate getTransferDate() { return transferDate; } public void setTransferDate(LocalDate v) { this.transferDate = v; }
    public Long getFromWarehouseId() { return fromWarehouseId; } public void setFromWarehouseId(Long v) { this.fromWarehouseId = v; }
    public Long getToWarehouseId() { return toWarehouseId; } public void setToWarehouseId(Long v) { this.toWarehouseId = v; }
    public String getStatus() { return status; } public void setStatus(String v) { this.status = v; }
    public String getRemarks() { return remarks; } public void setRemarks(String v) { this.remarks = v; }
    public List<StockTransferDtl> getDetails() { return details; }
}