package id.analisakoe.fin.entity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(name = "journal_hdr", schema = "fin")
public class JournalHdr {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "tenant_id") private Long tenantId;
    @Column(name = "company_id") private Long companyId;
    @Column(name = "doc_no") private String docNo;
    @Column(name = "doc_date") private LocalDate docDate;
    @Column(name = "journal_type") private String journalType;
    private String remarks;

    @OneToMany(mappedBy = "header", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JournalDtl> details = new ArrayList<>();

    public void addDetail(JournalDtl d) { details.add(d); d.setHeader(this); }
    public Long getId() { return id; }
    public String getDocNo() { return docNo; } public void setDocNo(String v) { this.docNo = v; }
    public LocalDate getDocDate() { return docDate; } public void setDocDate(LocalDate v) { this.docDate = v; }
    public String getJournalType() { return journalType; } public void setJournalType(String v) { this.journalType = v; }
    public String getRemarks() { return remarks; } public void setRemarks(String v) { this.remarks = v; }
    public Long getTenantId() { return tenantId; } public void setTenantId(Long v) { this.tenantId = v; }
    public Long getCompanyId() { return companyId; } public void setCompanyId(Long v) { this.companyId = v; }
    public List<JournalDtl> getDetails() { return details; }
}