package id.analisakoe.sal.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity @Table(name = "sales_return_dtl", schema = "sal")
public class SalesReturnDtl {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "header_id") private SalesReturnHdr header;
    @Column(name = "item_id") private Long itemId;
    @Column(name = "uom_id") private Long uomId;
    private BigDecimal qty;
    private BigDecimal price;
    private BigDecimal total;

    public void setHeader(SalesReturnHdr h) { this.header = h; }
    public Long getItemId() { return itemId; } public void setItemId(Long v) { this.itemId = v; }
    public Long getUomId() { return uomId; } public void setUomId(Long v) { this.uomId = v; }
    public BigDecimal getQty() { return qty; } public void setQty(BigDecimal v) { this.qty = v; }
    public BigDecimal getPrice() { return price; } public void setPrice(BigDecimal v) { this.price = v; }
    public BigDecimal getTotal() { return total; } public void setTotal(BigDecimal v) { this.total = v; }
}