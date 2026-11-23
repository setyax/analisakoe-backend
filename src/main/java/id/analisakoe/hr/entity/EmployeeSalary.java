package id.analisakoe.hr.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
@Entity @Table(name = "employee_salary", schema = "hr")
public class EmployeeSalary {
    @Id @Column(name = "employee_id") private Long employeeId;
    @Column(name = "base_salary") private BigDecimal baseSalary;
    @Column(name = "fixed_allowance") private BigDecimal fixedAllowance;
    @Column(name = "bpjs_kesehatan_rate") private BigDecimal bpjsKesehatanRate;
    @Column(name = "bpjs_jht_rate") private BigDecimal bpjsJhtRate;
    @Column(name = "bpjs_jp_rate") private BigDecimal bpjsJpRate;
    @Column(name = "ptkp_status") private String ptkpStatus;

    public Long getEmployeeId() { return employeeId; } public void setEmployeeId(Long v) { this.employeeId = v; }
    public BigDecimal getBaseSalary() { return baseSalary; } public void setBaseSalary(BigDecimal v) { this.baseSalary = v; }
    public BigDecimal getFixedAllowance() { return fixedAllowance; } public void setFixedAllowance(BigDecimal v) { this.fixedAllowance = v; }
    public BigDecimal getBpjsKesehatanRate() { return bpjsKesehatanRate; } public void setBpjsKesehatanRate(BigDecimal v) { this.bpjsKesehatanRate = v; }
    public BigDecimal getBpjsJhtRate() { return bpjsJhtRate; } public void setBpjsJhtRate(BigDecimal v) { this.bpjsJhtRate = v; }
    public BigDecimal getBpjsJpRate() { return bpjsJpRate; } public void setBpjsJpRate(BigDecimal v) { this.bpjsJpRate = v; }
    public String getPtkpStatus() { return ptkpStatus; } public void setPtkpStatus(String v) { this.ptkpStatus = v; }
}