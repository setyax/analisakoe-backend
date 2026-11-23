package id.analisakoe.core.entity;

import jakarta.persistence.*;

@Entity @Table(name = "number_range", schema = "core")
public class NumberRange {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long tenantId;
    private String docType;
    private String prefix;
    private String period;
    private Integer currentNumber;

    public NumberRange() {}
    public NumberRange(Long tenantId, String docType, String prefix, String period, Integer currentNumber) {
        this.tenantId = tenantId; this.docType = docType; this.prefix = prefix; this.period = period; this.currentNumber = currentNumber;
    }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; } public void setTenantId(Long v) { this.tenantId = v; }
    public String getDocType() { return docType; } public void setDocType(String v) { this.docType = v; }
    public String getPrefix() { return prefix; } public void setPrefix(String v) { this.prefix = v; }
    public String getPeriod() { return period; } public void setPeriod(String v) { this.period = v; }
    public Integer getCurrentNumber() { return currentNumber; } public void setCurrentNumber(Integer v) { this.currentNumber = v; }
}