package id.analisakoe.pur.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity @Table(name = "grn_dtl", schema = "pur")
public class GrnDtl {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "header_id") private GrnHdr header;
    @Column(name = "item_id") private Long itemId;
    @Column(name = "uom_id") private Long uomId;
    @Column(name = "qty_received") private BigDecimal qtyReceived;
    
	public Long getId() { return id; }
    public void setHeader(GrnHdr h) { this.header = h; }
    public Long getItemId() { return itemId; } public void setItemId(Long v) { this.itemId = v; }
    public Long getUomId() { return uomId; } public void setUomId(Long v) { this.uomId = v; }
    public BigDecimal getQtyReceived() { return qtyReceived; } public void setQtyReceived(BigDecimal v) { this.qtyReceived = v; }
}