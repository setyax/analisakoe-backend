package id.analisakoe.fin.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity @Table(name = "journal_dtl", schema = "fin")
public class JournalDtl {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "header_id") private JournalHdr header;
    @Column(name = "account_id") private Long accountId;
    private BigDecimal debit;
    private BigDecimal credit;
    private String description;

    public void setHeader(JournalHdr h) { this.header = h; }
    public Long getAccountId() { return accountId; } public void setAccountId(Long v) { this.accountId = v; }
    public BigDecimal getDebit() { return debit; } public void setDebit(BigDecimal v) { this.debit = v; }
    public BigDecimal getCredit() { return credit; } public void setCredit(BigDecimal v) { this.credit = v; }
    public String getDescription() { return description; } public void setDescription(String v) { this.description = v; }
}