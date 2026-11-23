package id.analisakoe.sal.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "sales_order_dtl", schema = "sal")
public class SalesOrderDtl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "header_id")
    private SalesOrderHdr header;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "uom_id", nullable = false)
    private Long uomId;

    @Column(nullable = false)
    private BigDecimal qty;

    @Column(nullable = false)
    private BigDecimal price;
    private BigDecimal subtotal;
    private BigDecimal dpp;

    @Column(name = "tax_amount")
    private BigDecimal taxAmount;

    @Column(name = "line_total")
    private BigDecimal lineTotal;

    public Long getId() { return id; }
    public SalesOrderHdr getHeader() { return header; }
    public void setHeader(SalesOrderHdr v) { this.header = v; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long v) { this.itemId = v; }
    public Long getUomId() { return uomId; }
    public void setUomId(Long v) { this.uomId = v; }
    public BigDecimal getQty() { return qty; }
    public void setQty(BigDecimal v) { this.qty = v; }
    public BigDecimal getSubtotal() { return subtotal; } public void setSubtotal(BigDecimal v) { this.subtotal = v; }
    public BigDecimal getDpp() { return dpp; } public void setDpp(BigDecimal v) { this.dpp = v; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal v) { this.price = v; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal v) { this.taxAmount = v; }
    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal v) { this.lineTotal = v; }
}