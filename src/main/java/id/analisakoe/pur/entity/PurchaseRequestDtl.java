package id.analisakoe.pur.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity @Table(name = "purchase_request_dtl", schema = "pur")
public class PurchaseRequestDtl {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "header_id") private PurchaseRequestHdr header;
    @Column(name = "item_id") private Long itemId;
    @Column(name = "uom_id") private Long uomId;
    private BigDecimal qty;
    @Column(name = "short_text") private String shortText;
    @Column(name = "delivery_date") private java.time.LocalDate deliveryDate;
    @Column(name = "estimated_price") private BigDecimal estimatedPrice;
    @Column(name = "account_assignment") private String accountAssignment;
    @Column(name = "material_group") private String materialGroup;
    @Column(name = "converted_po_id") private Long convertedPoId;
    
    public void setHeader(PurchaseRequestHdr h) { this.header = h; }
    public Long getItemId() { return itemId; } public void setItemId(Long v) { this.itemId = v; }
    public Long getUomId() { return uomId; } public void setUomId(Long v) { this.uomId = v; }
    public String getShortText() { return shortText; } public void setShortText(String v) { this.shortText = v; }
    public java.time.LocalDate getDeliveryDate() { return deliveryDate; } public void setDeliveryDate(java.time.LocalDate v) { this.deliveryDate = v; }
    public BigDecimal getEstimatedPrice() { return estimatedPrice; } public void setEstimatedPrice(BigDecimal v) { this.estimatedPrice = v; }
    public String getAccountAssignment() { return accountAssignment; } public void setAccountAssignment(String v) { this.accountAssignment = v; }
    public String getMaterialGroup() { return materialGroup; } public void setMaterialGroup(String v) { this.materialGroup = v; }
    public Long getConvertedPoId() { return convertedPoId; } public void setConvertedPoId(Long v) { this.convertedPoId = v; }
    public BigDecimal getQty() { return qty; } public void setQty(BigDecimal v) { this.qty = v; }
}