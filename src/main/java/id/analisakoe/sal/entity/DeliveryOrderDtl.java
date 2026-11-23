package id.analisakoe.sal.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
@Entity @Table(name = "delivery_order_dtl", schema = "sal")
public class DeliveryOrderDtl {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "header_id") private DeliveryOrderHdr header;
    private Long itemId; private Long uomId; private BigDecimal qty;
    public DeliveryOrderHdr getHeader() { return header; } public void setHeader(DeliveryOrderHdr v) { this.header = v; }
    public Long getItemId() { return itemId; } public void setItemId(Long v) { this.itemId = v; }
    public Long getUomId() { return uomId; } public void setUomId(Long v) { this.uomId = v; }
    public BigDecimal getQty() { return qty; } public void setQty(BigDecimal v) { this.qty = v; }
}