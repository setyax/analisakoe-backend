package id.analisakoe.pur.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity @Table(name = "purchase_order_dtl", schema = "pur")
public class PurchaseOrderDtl {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "header_id") private PurchaseOrderHdr header;
    @Column(name = "item_id") private Long itemId;
    @Column(name = "uom_id") private Long uomId;
    private BigDecimal qty;
    private BigDecimal price;
    private BigDecimal subtotal;
    private BigDecimal dpp;
    @Column(name = "tax_amount") private BigDecimal taxAmount;
    @Column(name = "line_total") private BigDecimal lineTotal;
    
    public void setHeader(PurchaseOrderHdr h) { this.header = h; }
    public Long getItemId() { return itemId; } public void setItemId(Long v) { this.itemId = v; }
    public Long getUomId() { return uomId; } public void setUomId(Long v) { this.uomId = v; }
    public BigDecimal getQty() { return qty; } public void setQty(BigDecimal v) { this.qty = v; }
    public BigDecimal getSubtotal() { return subtotal; } public void setSubtotal(BigDecimal v) { this.subtotal = v; }
    public BigDecimal getDpp() { return dpp; } public void setDpp(BigDecimal v) { this.dpp = v; }
    public BigDecimal getTaxAmount() { return taxAmount; } public void setTaxAmount(BigDecimal v) { this.taxAmount = v; }
    public BigDecimal getLineTotal() { return lineTotal; } public void setLineTotal(BigDecimal v) { this.lineTotal = v; }
    public BigDecimal getPrice() { return price; } public void setPrice(BigDecimal v) { this.price = v; }
}