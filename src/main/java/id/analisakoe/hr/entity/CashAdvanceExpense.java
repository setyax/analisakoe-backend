package id.analisakoe.hr.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity @Table(name = "cash_advance_expense", schema = "hr")
public class CashAdvanceExpense {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    
    @Column(name = "cash_advance_id") private Long cashAdvanceId;
    private String description;
    private BigDecimal amount;

    public Long getId() { return id; }
    public Long getCashAdvanceId() { return cashAdvanceId; } public void setCashAdvanceId(Long v) { this.cashAdvanceId = v; }
    public String getDescription() { return description; } public void setDescription(String v) { this.description = v; }
    public BigDecimal getAmount() { return amount; } public void setAmount(BigDecimal v) { this.amount = v; }
}