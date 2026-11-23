package id.analisakoe.pur.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "purchase_policy", schema = "pur")
public class PurchasePolicy {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false) private Long companyId;
    @Column(name = "rule_name", nullable = false, length = 100) private String ruleName;
    @Column(name = "rule_type", nullable = false, length = 30) private String ruleType;
    @Column(name = "threshold_value", precision = 18, scale = 4) private BigDecimal thresholdValue;
    @Column(name = "reference_id") private Long referenceId;
    @Column(name = "requires_pr", nullable = false) private boolean requiresPr;
    @Column(name = "priority", nullable = false) private Integer priority = 100;
    @Column(name = "active", nullable = false) private boolean active = true;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long v) { this.companyId = v; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String v) { this.ruleName = v; }
    public String getRuleType() { return ruleType; }
    public void setRuleType(String v) { this.ruleType = v; }
    public BigDecimal getThresholdValue() { return thresholdValue; }
    public void setThresholdValue(BigDecimal v) { this.thresholdValue = v; }
    public Long getReferenceId() { return referenceId; }
    public void setReferenceId(Long v) { this.referenceId = v; }
    public boolean isRequiresPr() { return requiresPr; }
    public void setRequiresPr(boolean v) { this.requiresPr = v; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer v) { this.priority = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}
