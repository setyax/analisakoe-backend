package id.analisakoe.inv.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "transaction_detail", schema = "inv")
public class InvTransactionDetail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "header_id", nullable = false)
    private InvTransactionHeader header;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "uom_id", nullable = false)
    private Long uomId;

    @Column(nullable = false)
    private BigDecimal qty;

    public Long getId() { return id; }
    public InvTransactionHeader getHeader() { return header; }
    public void setHeader(InvTransactionHeader v) { this.header = v; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long v) { this.itemId = v; }
    public Long getUomId() { return uomId; }
    public void setUomId(Long v) { this.uomId = v; }
    public BigDecimal getQty() { return qty; }
    public void setQty(BigDecimal v) { this.qty = v; }
}