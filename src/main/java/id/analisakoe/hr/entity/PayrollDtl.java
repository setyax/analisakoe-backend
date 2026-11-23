package id.analisakoe.hr.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
@Entity @Table(name = "payroll_dtl", schema = "hr")
public class PayrollDtl {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "payroll_run_id") private PayrollRun payrollRun;
    @Column(name = "employee_id") private Long employeeId;
    @Column(name = "gross_salary") private BigDecimal grossSalary;
    @Column(name = "bpjs_deduction") private BigDecimal bpjsDeduction;
    @Column(name = "pph21_deduction") private BigDecimal pph21Deduction;
    @Column(name = "net_salary") private BigDecimal netSalary;

    public void setPayrollRun(PayrollRun r) { this.payrollRun = r; }
    public Long getEmployeeId() { return employeeId; } public void setEmployeeId(Long v) { this.employeeId = v; }
    public BigDecimal getGrossSalary() { return grossSalary; } public void setGrossSalary(BigDecimal v) { this.grossSalary = v; }
    public BigDecimal getBpjsDeduction() { return bpjsDeduction; } public void setBpjsDeduction(BigDecimal v) { this.bpjsDeduction = v; }
    public BigDecimal getPph21Deduction() { return pph21Deduction; } public void setPph21Deduction(BigDecimal v) { this.pph21Deduction = v; }
    public BigDecimal getNetSalary() { return netSalary; } public void setNetSalary(BigDecimal v) { this.netSalary = v; }
}